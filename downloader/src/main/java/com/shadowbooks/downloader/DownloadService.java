package com.shadowbooks.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foreground-service downloader for the large ShadowBooks APK.
 *
 * Why a service: the download must survive the activity being closed, the screen
 * sleeping, the network dropping, and even the phone restarting. The file is fetched
 * with several parallel HTTP Range connections (each starting from the permanent
 * GitHub Release URL, so every chunk gets a fresh signed URL). Every connection owns
 * one segment of ShadowBooks.apk.part and its progress is persisted, so any stop —
 * pause, crash, reboot — resumes exactly where it left off instead of starting over.
 * When all segments finish, the file is verified (size + SHA-256 from the GitHub
 * release) and published into the phone's public Downloads folder as ShadowBooks.apk.
 */
public final class DownloadService extends Service {
    public static final String ACTION_START = "com.shadowbooks.downloader.action.START";
    public static final String ACTION_PAUSE = "com.shadowbooks.downloader.action.PAUSE";

    static final String PREFS = "download_state";
    static final String APK_NAME = "ShadowBooks.apk";

    private static final String RELEASE_API_URL =
            "https://api.github.com/repos/mhameed716/ShadowBooks/releases/tags/v1.0";
    private static final String FALLBACK_DOWNLOAD_URL =
            "https://github.com/mhameed716/ShadowBooks/releases/download/v1.0/ShadowBooks.apk";
    private static final String PARTIAL_NAME = "ShadowBooks.apk.part";
    private static final String STATE_NAME = "ShadowBooks.apk.parts";

    private static final String CHANNEL_ID = "shadowbooks_download";
    private static final int NOTIFICATION_ID = 41;
    private static final int SEGMENT_COUNT = 8;
    private static final int CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_CHUNK_ATTEMPTS = 6;

    private final ExecutorService controller = Executors.newSingleThreadExecutor();
    private final Set<HttpURLConnection> activeConnections = ConcurrentHashMap.newKeySet();

    private volatile boolean started;
    private volatile boolean stopRequested;
    private volatile boolean userPaused;
    private ExecutorService workers;
    private SegmentState currentState;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notifications;
    private SharedPreferences prefs;

    // Speed sampling.
    private long lastSampleAt;
    private long lastSampleBytes;
    private double speedBytesPerSecond;

    static void start(Context context) {
        Intent intent = new Intent(context, DownloadService.class).setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    static File downloadDir(Context context) {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return directory != null ? directory : context.getFilesDir();
    }

    static File partFile(Context context) {
        return new File(downloadDir(context), PARTIAL_NAME);
    }

    static File stateFile(Context context) {
        return new File(downloadDir(context), STATE_NAME);
    }

    static File publicApkFile() {
        return new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                APK_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "تنزيل ShadowBooks", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("تقدّم تنزيل تطبيق ShadowBooks");
            notifications.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            pauseDownload();
            return START_NOT_STICKY;
        }
        // A null intent means the system restarted the sticky service after killing it:
        // fall through and resume whatever partial download is on disk.
        if (started) return START_STICKY;
        if (prefs.getBoolean("userPaused", false) && intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        started = true;
        stopRequested = false;
        userPaused = false;
        prefs.edit().putBoolean("userPaused", false).apply();
        startForeground(NOTIFICATION_ID, buildProgressNotification(
                "جارٍ الاتصال بـ GitHub...", 0, 0, true));
        controller.execute(this::runLoop);
        return START_STICKY;
    }

    private void pauseDownload() {
        userPaused = true;
        stopRequested = true;
        prefs.edit().putBoolean("userPaused", true).apply();
        disconnectActive();
        if (workers != null) workers.shutdownNow();
        if (!started) {
            writeStatus("paused", 0, 0, 0,
                    "التنزيل متوقف مؤقتًا. الجزء الذي تم تحميله محفوظ ويمكن استئنافه في أي وقت.");
            stopSelf();
        }
        // If started, runLoop notices stopRequested, persists state, and stops the service.
    }

    private void runLoop() {
        acquireWakeLock();
        int integrityRestarts = 0;
        try {
            while (!stopRequested) {
                try {
                    downloadAndPublish();
                    return; // Completed (status + final notification already posted).
                } catch (StopException stopped) {
                    break;
                } catch (ResetDownloadException reset) {
                    wipePartial();
                    if (++integrityRestarts > 1) {
                        finishWithError("لم يطابق الملف المنزّل بصمة GitHub مرتين متتاليتين. "
                                + "تحقق من اتصالك ثم أعد التنزيل.");
                        return;
                    }
                    // Loop restarts the download from scratch automatically.
                } catch (FatalDownloadException fatal) {
                    finishWithError(fatal.getMessage());
                    return;
                } catch (IOException network) {
                    if (stopRequested) break;
                    writeStatus("waiting", lastKnownDone(), lastKnownTotal(), 0,
                            "انقطع الاتصال أو توقفت الشبكة. الجزء المنزّل محفوظ، "
                                    + "وستُستأنف المحاولة تلقائيًا عند عودة الإنترنت...");
                    notifyProgress("بانتظار عودة الإنترنت لاستئناف التنزيل تلقائيًا...",
                            lastKnownDone(), lastKnownTotal());
                    if (!sleepBeforeRetry()) break;
                }
            }
            // Stopped (paused or destroyed): keep every downloaded byte for later.
            if (currentState != null) persistState(currentState);
            if (userPaused) {
                writeStatus("paused", lastKnownDone(), lastKnownTotal(), 0,
                        "التنزيل متوقف مؤقتًا. الجزء الذي تم تحميله محفوظ ويمكن استئنافه في أي وقت.");
                postFinalNotification("التنزيل متوقف مؤقتًا",
                        "اضغط لفتح المنزّل واستئناف التنزيل من حيث توقف.");
            }
        } finally {
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
        }
    }

    private void downloadAndPublish() throws IOException {
        ReleaseInfo info = resolveReleaseInfo();
        SegmentState state = prepareState(info);
        currentState = state;
        runWorkers(state);
        if (stopRequested) throw new StopException();

        writeStatus("verifying", info.size, info.size, 0,
                "اكتمل التنزيل؛ جارٍ التحقق من سلامة الملف...");
        notifyProgress("اكتمل التنزيل؛ جارٍ التحقق من سلامة الملف...", info.size, info.size);
        File part = partFile(this);
        if (part.length() != info.size) {
            throw new ResetDownloadException("حجم الملف لا يطابق حجم GitHub.");
        }
        if (info.sha256 != null) {
            String actual = sha256(part);
            if (!info.sha256.equalsIgnoreCase(actual)) {
                throw new ResetDownloadException("بصمة الملف لا تطابق بصمة GitHub.");
            }
        }

        writeStatus("publishing", info.size, info.size, 0,
                "جارٍ حفظ ShadowBooks في مجلد التنزيلات على الهاتف...");
        notifyProgress("جارٍ حفظ التطبيق في مجلد التنزيلات...", info.size, info.size);
        String completeUri = publishToDownloads(part, info);
        wipePartial();
        currentState = null;
        prefs.edit()
                .putString("status", "complete")
                .putLong("downloaded", info.size)
                .putLong("total", info.size)
                .putLong("speed", 0)
                .putString("completeUri", completeUri)
                .putString("message", "تم تنزيل ShadowBooks والتحقق منه، وهو محفوظ الآن "
                        + "في مجلد التنزيلات (Downloads) باسم ShadowBooks.apk. اضغط «تثبيت التطبيق».")
                .putLong("heartbeat", System.currentTimeMillis())
                .apply();
        postFinalNotification("اكتمل تنزيل ShadowBooks",
                "التطبيق محفوظ في مجلد التنزيلات. اضغط هنا لتثبيته.");
    }

    /**
     * Release size/SHA come from the GitHub API. If the network is down but a previous
     * session already recorded them, resume offline with the saved fingerprint; a changed
     * release is then still detected on the next successful API contact in prepareState.
     */
    private ReleaseInfo resolveReleaseInfo() throws IOException {
        try {
            return fetchReleaseInfo();
        } catch (IOException error) {
            SegmentState cached = readStateFile();
            if (cached != null && cached.size > 0) {
                return new ReleaseInfo(FALLBACK_DOWNLOAD_URL, cached.size, cached.sha256);
            }
            throw error;
        }
    }

    private SegmentState prepareState(ReleaseInfo info) throws IOException {
        if (info.size <= 0) {
            throw new IOException("تعذر معرفة الحجم الصحيح لملف ShadowBooks من GitHub.");
        }
        File directory = downloadDir(this);
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new FatalDownloadException("تعذر إنشاء مجلد التنزيل على الهاتف.");
        }
        File part = partFile(this);
        SegmentState existing = readStateFile();
        boolean reusable = existing != null
                && existing.size == info.size
                && (existing.sha256 == null || info.sha256 == null
                        || existing.sha256.equalsIgnoreCase(info.sha256))
                && part.isFile();
        SegmentState state;
        if (reusable) {
            state = existing;
            state.clamp(part);
            if (part.length() != info.size) {
                try (RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
                    raf.setLength(info.size);
                }
            }
        } else {
            // Fresh download, or the release asset changed since the partial was saved:
            // never mix bytes from two different builds of the APK.
            wipePartial();
            state = SegmentState.create(info);
            try (RandomAccessFile raf = new RandomAccessFile(part, "rw")) {
                raf.setLength(info.size);
            }
            persistState(state);
        }
        long freeSpace = directory.getUsableSpace();
        long remaining = info.size - state.totalDone();
        if (freeSpace > 0 && freeSpace < remaining + 20L * 1024L * 1024L) {
            throw new FatalDownloadException(
                    "لا توجد مساحة كافية لإكمال التنزيل. فرّغ مساحة على الهاتف ثم أعد المحاولة.");
        }
        return state;
    }

    private void runWorkers(SegmentState state) throws IOException {
        File part = partFile(this);
        writeStatus("running", state.totalDone(), state.size, 0,
                state.totalDone() > 0
                        ? "جارٍ استئناف التنزيل من الجزء المحفوظ..."
                        : "جارٍ تنزيل ShadowBooks بعدة اتصالات متوازية...");
        workers = Executors.newFixedThreadPool(Math.max(1, state.count()));
        try {
            Future<?>[] futures = new Future<?>[state.count()];
            for (int i = 0; i < state.count(); i++) {
                final int segment = i;
                futures[i] = workers.submit(() -> {
                    downloadSegment(state, segment, part);
                    return null;
                });
            }
            lastSampleAt = System.currentTimeMillis();
            lastSampleBytes = state.totalDone();
            speedBytesPerSecond = 0;
            long lastPersistAt = 0;
            boolean allDone = false;
            while (!allDone) {
                if (stopRequested) throw new StopException();
                allDone = true;
                for (Future<?> future : futures) {
                    if (!future.isDone()) { allDone = false; break; }
                }
                long now = System.currentTimeMillis();
                if (wakeLock == null || !wakeLock.isHeld()) acquireWakeLock();
                updateSpeed(state.totalDone(), now);
                writeStatus("running", state.totalDone(), state.size,
                        Math.round(speedBytesPerSecond), runningMessage(state));
                notifyProgress(runningMessage(state), state.totalDone(), state.size);
                if (now - lastPersistAt >= 2000) {
                    persistState(state);
                    lastPersistAt = now;
                }
                if (!allDone) {
                    try {
                        Thread.sleep(700);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new StopException();
                    }
                }
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new StopException();
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof IOException) throw (IOException) cause;
                    throw new IOException(cause);
                }
            }
            persistState(state);
        } finally {
            workers.shutdownNow();
            workers = null;
        }
    }

    private void downloadSegment(SegmentState state, int segment, File part) throws IOException {
        long end = state.ends[segment];
        long position = state.done.get(segment);
        if (position > end + 1) position = state.starts[segment];
        try (RandomAccessFile output = new RandomAccessFile(part, "rw")) {
            while (position <= end) {
                if (stopRequested || Thread.currentThread().isInterrupted()) {
                    throw new StopException();
                }
                long chunkEnd = Math.min(end, position + CHUNK_SIZE - 1L);
                byte[] chunk = downloadRangeWithRetries(
                        FALLBACK_DOWNLOAD_URL, position, chunkEnd);
                output.seek(position);
                output.write(chunk);
                position += chunk.length;
                state.done.set(segment, position);
            }
        }
    }

    private byte[] downloadRangeWithRetries(String url, long start, long end) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_CHUNK_ATTEMPTS; attempt++) {
            if (stopRequested) throw new StopException();
            try {
                return downloadRangeOnce(url, start, end);
            } catch (ResetDownloadException | StopException error) {
                throw error;
            } catch (IOException error) {
                lastError = error;
                if (attempt == MAX_CHUNK_ATTEMPTS) break;
                sleepInterruptibly(Math.min(15_000L, attempt * 2_000L));
            }
        }
        throw lastError == null ? new IOException("تعذر تنزيل أحد أجزاء الملف.") : lastError;
    }

    private byte[] downloadRangeOnce(String initialUrl, long start, long end) throws IOException {
        String currentUrl = initialUrl;
        long expectedLengthLong = end - start + 1L;
        if (expectedLengthLong <= 0 || expectedLengthLong > Integer.MAX_VALUE) {
            throw new IOException("نطاق تنزيل غير صالح.");
        }
        int expectedLength = (int) expectedLengthLong;

        for (int redirect = 0; redirect < 7; redirect++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                activeConnections.add(connection);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(30_000);
                connection.setReadTimeout(60_000);
                connection.setRequestProperty("User-Agent", "ShadowBooks-Downloader/2.0");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Range", "bytes=" + start + "-" + end);

                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("لم يرجع GitHub رابط تنزيل صالحًا.");
                    }
                    currentUrl = new URL(new URL(currentUrl), location).toString();
                    continue;
                }
                if (code == 416) {
                    // The recorded segment state no longer matches the server file:
                    // safest is a clean restart, handled by runLoop.
                    throw new ResetDownloadException("رفض الخادم استئناف هذا الجزء (416).");
                }
                if (code != 206) {
                    throw new IOException("لم يقبل خادم التنزيل طلب الجزء (HTTP " + code + ").");
                }
                String contentRange = connection.getHeaderField("Content-Range");
                String expectedPrefix = "bytes " + start + "-";
                if (contentRange == null || !contentRange.startsWith(expectedPrefix)) {
                    throw new IOException("رجع جزء غير متوقع من خادم التنزيل.");
                }
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength)) {
                    byte[] buffer = new byte[64 * 1024];
                    int total = 0;
                    while (total < expectedLength) {
                        if (stopRequested) throw new StopException();
                        int count = input.read(buffer, 0,
                                Math.min(buffer.length, expectedLength - total));
                        if (count < 0) break;
                        output.write(buffer, 0, count);
                        total += count;
                    }
                    if (total != expectedLength) {
                        throw new IOException("انقطع تنزيل أحد الأجزاء قبل اكتماله.");
                    }
                    return output.toByteArray();
                }
            } finally {
                if (connection != null) {
                    activeConnections.remove(connection);
                    connection.disconnect();
                }
            }
        }
        throw new IOException("حدثت عمليات إعادة توجيه كثيرة أثناء تنزيل أحد الأجزاء.");
    }

    /** Copy the verified file into the phone's public Downloads folder. */
    private String publishToDownloads(File part, ReleaseInfo info) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = getContentResolver();
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            // Remove earlier copies owned by this app so the name stays ShadowBooks.apk.
            try (Cursor cursor = resolver.query(collection,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + "=?",
                    new String[]{APK_NAME}, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        try {
                            resolver.delete(ContentUrisCompat.withId(collection, id), null, null);
                        } catch (Exception ignored) {
                            // Not ours or already gone; insert below still succeeds.
                        }
                    }
                }
            } catch (Exception ignored) {
                // Query support varies; publishing below is what matters.
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, APK_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE,
                    "application/vnd.android.package-archive");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = resolver.insert(collection, values);
            if (uri == null) {
                throw new FatalDownloadException("تعذر إنشاء ملف ShadowBooks في مجلد التنزيلات.");
            }
            try {
                String copiedHash;
                try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                    if (output == null) {
                        throw new FatalDownloadException(
                                "تعذر الكتابة في مجلد التنزيلات على الهاتف.");
                    }
                    copiedHash = copyAndDigest(part, output);
                }
                if (info.sha256 != null && !info.sha256.equalsIgnoreCase(copiedHash)) {
                    resolver.delete(uri, null, null);
                    throw new FatalDownloadException(
                            "حدث خطأ أثناء نسخ الملف إلى مجلد التنزيلات. أعد فتح المنزّل للمحاولة.");
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, done, null, null);
                return uri.toString();
            } catch (FatalDownloadException fatal) {
                throw fatal;
            } catch (IOException | RuntimeException error) {
                try {
                    resolver.delete(uri, null, null);
                } catch (Exception ignored) {
                }
                throw new FatalDownloadException(
                        "تعذر حفظ الملف في مجلد التنزيلات: " + error.getMessage());
            }
        }
        File target = publicApkFile();
        File directory = target.getParentFile();
        if (directory != null && !directory.exists()
                && !directory.mkdirs() && !directory.exists()) {
            throw new FatalDownloadException("تعذر الوصول إلى مجلد التنزيلات على الهاتف.");
        }
        String copiedHash;
        try (OutputStream output = new FileOutputStream(target)) {
            copiedHash = copyAndDigest(part, output);
        } catch (IOException error) {
            throw new FatalDownloadException("تعذر حفظ الملف في مجلد التنزيلات. "
                    + "تأكد من منح إذن التخزين ثم أعد المحاولة.");
        }
        if (info.sha256 != null && !info.sha256.equalsIgnoreCase(copiedHash)) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw new FatalDownloadException(
                    "حدث خطأ أثناء نسخ الملف إلى مجلد التنزيلات. أعد فتح المنزّل للمحاولة.");
        }
        return "";
    }

    private ReleaseInfo fetchReleaseInfo() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API_URL).openConnection();
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "ShadowBooks-Downloader/2.0");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException(
                        "تعذر جلب معلومات إصدار ShadowBooks من GitHub (HTTP " + code + ").");
            }
            String json = new String(readAll(connection.getInputStream()), StandardCharsets.UTF_8);
            int assetNameIndex = json.indexOf("\"" + APK_NAME + "\"");
            String scope = assetNameIndex >= 0
                    ? json.substring(assetNameIndex, Math.min(json.length(), assetNameIndex + 4096))
                    : json;
            String browserDownloadUrl = findString(scope,
                    "\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
            Long size = findLong(scope, "\"size\"\\s*:\\s*(\\d+)");
            String sha256 = findString(scope, "\"digest\"\\s*:\\s*\"sha256:([0-9a-fA-F]{64})\"");
            return new ReleaseInfo(
                    browserDownloadUrl == null ? FALLBACK_DOWNLOAD_URL : browserDownloadUrl,
                    size == null ? -1L : size,
                    sha256);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ------------------------------------------------------------------ state file

    private SegmentState readStateFile() {
        File file = stateFile(this);
        if (!file.isFile()) return null;
        try (InputStream input = new FileInputStream(file)) {
            Properties properties = new Properties();
            properties.load(input);
            long size = Long.parseLong(properties.getProperty("size", "0"));
            int count = Integer.parseInt(properties.getProperty("count", "0"));
            if (size <= 0 || count <= 0 || count > 64) return null;
            SegmentState state = new SegmentState(size,
                    emptyToNull(properties.getProperty("sha")), count);
            for (int i = 0; i < count; i++) {
                state.starts[i] = Long.parseLong(properties.getProperty("start" + i, "0"));
                state.ends[i] = Long.parseLong(properties.getProperty("end" + i, "-1"));
                state.done.set(i, Long.parseLong(
                        properties.getProperty("done" + i, String.valueOf(state.starts[i]))));
            }
            return state;
        } catch (Exception invalid) {
            return null;
        }
    }

    private synchronized void persistState(SegmentState state) {
        try {
            Properties properties = new Properties();
            properties.setProperty("size", String.valueOf(state.size));
            properties.setProperty("sha", state.sha256 == null ? "" : state.sha256);
            properties.setProperty("count", String.valueOf(state.count()));
            for (int i = 0; i < state.count(); i++) {
                properties.setProperty("start" + i, String.valueOf(state.starts[i]));
                properties.setProperty("end" + i, String.valueOf(state.ends[i]));
                properties.setProperty("done" + i, String.valueOf(state.done.get(i)));
            }
            File temp = new File(downloadDir(this), STATE_NAME + ".tmp");
            try (OutputStream output = new FileOutputStream(temp)) {
                properties.store(output, "ShadowBooks download segments");
            }
            if (!temp.renameTo(stateFile(this))) {
                try (OutputStream output = new FileOutputStream(stateFile(this))) {
                    properties.store(output, "ShadowBooks download segments");
                }
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        } catch (Exception ignored) {
            // A failed bookkeeping write must not kill the transfer; the next
            // periodic persist retries, and worst case a few megabytes re-download.
        }
    }

    private void wipePartial() {
        //noinspection ResultOfMethodCallIgnored
        partFile(this).delete();
        //noinspection ResultOfMethodCallIgnored
        stateFile(this).delete();
    }

    // ------------------------------------------------------------------ UI / status

    private void writeStatus(String status, long downloaded, long total,
                             long speed, String message) {
        prefs.edit()
                .putString("status", status)
                .putLong("downloaded", downloaded)
                .putLong("total", total)
                .putLong("speed", speed)
                .putString("message", message)
                .putLong("heartbeat", System.currentTimeMillis())
                .apply();
    }

    private String runningMessage(SegmentState state) {
        long done = state.totalDone();
        double percent = state.size > 0 ? done * 100.0 / state.size : 0.0;
        StringBuilder text = new StringBuilder("جارٍ تنزيل ShadowBooks بعدة اتصالات متوازية\n");
        text.append(formatSize(done)).append(" من ").append(formatSize(state.size));
        text.append(String.format(Locale.US, " (%.1f%%)", percent));
        if (speedBytesPerSecond > 1024) {
            text.append("\nالسرعة: ").append(formatSize(Math.round(speedBytesPerSecond)))
                    .append("/ث");
            long remaining = state.size - done;
            long etaSeconds = Math.round(remaining / speedBytesPerSecond);
            if (etaSeconds > 0) {
                text.append(" — تبقّى نحو ").append(formatDuration(etaSeconds));
            }
        }
        return text.toString();
    }

    private void updateSpeed(long doneBytes, long now) {
        long elapsed = now - lastSampleAt;
        if (elapsed >= 500) {
            double instant = (doneBytes - lastSampleBytes) * 1000.0 / elapsed;
            speedBytesPerSecond = speedBytesPerSecond <= 0
                    ? instant
                    : speedBytesPerSecond * 0.65 + instant * 0.35;
            lastSampleAt = now;
            lastSampleBytes = doneBytes;
        }
    }

    private Notification buildProgressNotification(String text, long done, long total,
                                                   boolean indeterminate) {
        int progress = total > 0 ? (int) Math.min(1000L, done * 1000L / total) : 0;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("منزّل ShadowBooks")
                .setContentText(flatten(text))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setProgress(1000, progress, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent());
        Intent pause = new Intent(this, DownloadService.class).setAction(ACTION_PAUSE);
        builder.addAction(android.R.drawable.ic_media_pause, "إيقاف مؤقت",
                PendingIntent.getService(this, 3, pause,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return builder.build();
    }

    private void notifyProgress(String text, long done, long total) {
        try {
            notifications.notify(NOTIFICATION_ID,
                    buildProgressNotification(text, done, total, total <= 0));
        } catch (Exception ignored) {
            // Notification permission can be denied; the transfer itself continues.
        }
    }

    private void postFinalNotification(String title, String text) {
        try {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .build();
            notifications.notify(NOTIFICATION_ID, notification);
        } catch (Exception ignored) {
        }
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 4, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void finishWithError(String message) {
        writeStatus("error", lastKnownDone(), lastKnownTotal(), 0,
                "حدثت مشكلة في التنزيل: " + message
                        + "\nأي جزء تم تحميله محفوظ؛ أعد فتح المنزّل للاستئناف.");
        postFinalNotification("توقف تنزيل ShadowBooks",
                "اضغط لفتح المنزّل واستئناف التنزيل من حيث توقف.");
    }

    private long lastKnownDone() {
        return currentState != null ? currentState.totalDone()
                : prefs.getLong("downloaded", 0);
    }

    private long lastKnownTotal() {
        return currentState != null ? currentState.size : prefs.getLong("total", 0);
    }

    // ------------------------------------------------------------------ helpers

    private boolean sleepBeforeRetry() {
        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + 30_000L;
        while (!stopRequested && System.currentTimeMillis() < deadline) {
            prefs.edit().putLong("heartbeat", System.currentTimeMillis()).apply();
            if (isNetworkAvailable()
                    && System.currentTimeMillis() - startedAt > 3000) return true;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !stopRequested;
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception error) {
            return false;
        }
    }

    private void sleepInterruptibly(long millis) throws IOException {
        long remaining = millis;
        while (remaining > 0) {
            if (stopRequested) throw new StopException();
            long slice = Math.min(remaining, 250);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new StopException();
            }
            remaining -= slice;
        }
    }

    private void disconnectActive() {
        for (HttpURLConnection connection : activeConnections) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power == null) return;
            wakeLock = power.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "ShadowBooksDownloader:download");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(15 * 60 * 1000L);
        } catch (Exception ignored) {
            // The download still runs without the lock; it is only a keep-alive aid.
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        stopRequested = true;
        disconnectActive();
        if (workers != null) workers.shutdownNow();
        if (currentState != null) persistState(currentState);
        releaseWakeLock();
        controller.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static String copyAndDigest(File source, OutputStream output) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[256 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
            }
        }
        output.flush();
        return toHex(digest.digest());
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[256 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] hash) {
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream stream = new BufferedInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String findString(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Long findLong(String text, String regex) {
        String value = findString(text, regex);
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String flatten(String text) {
        return text.replace('\n', ' ');
    }

    static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " بايت";
        double megabytes = bytes / 1_048_576.0;
        if (megabytes < 1024.0) return String.format(Locale.US, "%.1f ميغابايت", megabytes);
        return String.format(Locale.US, "%.2f غيغابايت", megabytes / 1024.0);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 90) return "أقل من دقيقتين";
        long minutes = (seconds + 59) / 60;
        if (minutes < 90) return minutes + " دقيقة";
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " ساعة" : hours + " ساعة و" + rest + " دقيقة";
    }

    /** Helper kept tiny so the service can build MediaStore item URIs on API 29+. */
    private static final class ContentUrisCompat {
        static Uri withId(Uri contentUri, long id) {
            return android.content.ContentUris.withAppendedId(contentUri, id);
        }
    }

    private static final class SegmentState {
        final long size;
        final String sha256;
        final long[] starts;
        final long[] ends;
        final AtomicLongArray done;

        SegmentState(long size, String sha256, int count) {
            this.size = size;
            this.sha256 = sha256;
            this.starts = new long[count];
            this.ends = new long[count];
            this.done = new AtomicLongArray(count);
        }

        static SegmentState create(ReleaseInfo info) {
            int count = (int) Math.max(1,
                    Math.min(SEGMENT_COUNT, (info.size + CHUNK_SIZE - 1) / CHUNK_SIZE));
            SegmentState state = new SegmentState(info.size, info.sha256, count);
            long segmentLength = (info.size + count - 1) / count;
            for (int i = 0; i < count; i++) {
                state.starts[i] = i * segmentLength;
                state.ends[i] = Math.min(info.size - 1, state.starts[i] + segmentLength - 1);
                state.done.set(i, state.starts[i]);
            }
            return state;
        }

        int count() {
            return starts.length;
        }

        long totalDone() {
            long total = 0;
            for (int i = 0; i < count(); i++) {
                total += Math.max(0, done.get(i) - starts[i]);
            }
            return total;
        }

        void clamp(File part) {
            for (int i = 0; i < count(); i++) {
                long value = done.get(i);
                if (value < starts[i]) value = starts[i];
                if (value > ends[i] + 1) value = ends[i] + 1;
                done.set(i, value);
            }
        }
    }

    private static final class ReleaseInfo {
        final String browserDownloadUrl;
        final long size;
        final String sha256;

        ReleaseInfo(String browserDownloadUrl, long size, String sha256) {
            this.browserDownloadUrl = browserDownloadUrl;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /** The user (or shutdown) asked to stop: keep partial bytes, no error. */
    private static final class StopException extends IOException {
    }

    /** Partial bytes cannot be trusted (integrity/416): restart clean, automatically. */
    private static final class ResetDownloadException extends IOException {
        ResetDownloadException(String message) {
            super(message);
        }
    }

    /** Retrying will not help (space, Downloads access): tell the user and stop. */
    private static final class FatalDownloadException extends IOException {
        FatalDownloadException(String message) {
            super(message);
        }
    }
}
