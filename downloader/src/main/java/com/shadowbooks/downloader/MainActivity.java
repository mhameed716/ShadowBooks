package com.shadowbooks.downloader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny resumable downloader for the large ShadowBooks APK.
 *
 * The full APK is requested in small HTTP Range chunks. Every chunk starts from the
 * permanent GitHub Release URL, so GitHub can issue a fresh signed download URL for
 * that chunk instead of one long-lived browser transfer expiring in the middle.
 * Downloaded bytes stay in ShadowBooks.apk.part and can be resumed after the app,
 * network, or phone is restarted.
 */
public final class MainActivity extends Activity {
    private static final String RELEASE_API_URL =
            "https://api.github.com/repos/mhameed716/ShadowBooks/releases/tags/v1.0";
    private static final String FALLBACK_DOWNLOAD_URL =
            "https://github.com/mhameed716/ShadowBooks/releases/download/v1.0/ShadowBooks.apk";
    private static final String APK_NAME = "ShadowBooks.apk";
    private static final String PARTIAL_NAME = "ShadowBooks.apk.part";
    private static final int CHUNK_SIZE = 2 * 1024 * 1024;
    private static final int MAX_CHUNK_ATTEMPTS = 8;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView statusView;
    private ProgressBar progressBar;
    private Button actionButton;
    private volatile boolean running;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int padding = dp(24);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("منزّل ShadowBooks");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        layout.addView(title, matchWrap(0));

        TextView intro = new TextView(this);
        intro.setText("هذا المنزّل الصغير يحمّل تطبيق ShadowBooks الكبير على أجزاء صغيرة "
                + "قابلة للاستئناف. إذا انقطع الإنترنت أو أُغلق الهاتف، افتح المنزّل من جديد "
                + "واضغط «استئناف التنزيل» وسيكمل من المكان الذي توقف عنده، دون البدء من الصفر.");
        intro.setTextSize(17);
        intro.setGravity(Gravity.CENTER);
        layout.addView(intro, matchWrap(dp(22)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        layout.addView(progressBar, matchWrap(dp(28)));

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setGravity(Gravity.CENTER);
        layout.addView(statusView, matchWrap(dp(18)));

        actionButton = new Button(this);
        actionButton.setTextSize(18);
        actionButton.setOnClickListener(view -> onActionClicked());
        layout.addView(actionButton, matchWrap(dp(24)));

        TextView note = new TextView(this);
        note.setText("حجم التطبيق الكامل نحو 940 ميغابايت. اترك مساحة كافية على الهاتف، "
                + "ويفضل استخدام شبكة Wi-Fi مستقرة.");
        note.setTextSize(14);
        note.setGravity(Gravity.CENTER);
        layout.addView(note, matchWrap(dp(22)));

        setContentView(layout);
        refreshLocalState();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void onActionClicked() {
        if (running) return;
        setRunningUi(true, "جارٍ الاتصال بـ GitHub...");
        executor.execute(() -> {
            try {
                ReleaseInfo info;
                try {
                    info = fetchReleaseInfo();
                } catch (IOException connectionError) {
                    // A previously completed file is already trustworthy: it was renamed
                    // only after its size and SHA-256 were verified during download.
                    if (apkFile(false).isFile()) {
                        runOnUiThread(() -> {
                            setRunningUi(false, null);
                            refreshLocalState();
                            installDownloadedApk();
                        });
                        return;
                    }
                    throw connectionError;
                }
                File completeFile = apkFile(false);
                File partialFile = apkFile(true);

                if (isCompleteAndVerified(completeFile, info)) {
                    showStatus("ملف ShadowBooks مكتمل ومتحقق منه. اضغط «تثبيت التطبيق» عند ظهوره.");
                    runOnUiThread(() -> {
                        setRunningUi(false, null);
                        refreshLocalState();
                        installDownloadedApk();
                    });
                    return;
                }
                if (completeFile.exists() && !completeFile.delete()) {
                    throw new IOException("تعذر استبدال ملف سابق غير مكتمل التحقق: " + completeFile);
                }

                downloadApk(info, partialFile, completeFile);
                showStatus("اكتمل تنزيل ShadowBooks وتم التحقق من سلامته. اضغط «تثبيت التطبيق» عند ظهوره.");
                runOnUiThread(() -> {
                    setRunningUi(false, null);
                    refreshLocalState();
                    installDownloadedApk();
                });
            } catch (InterruptedIOException interrupted) {
                if (!destroyed) {
                    runOnUiThread(() -> {
                        setRunningUi(false, null);
                        refreshLocalState();
                        showStatus("توقف التنزيل. الجزء الذي تم تحميله محفوظ ويمكن استئنافه.");
                    });
                }
            } catch (Exception error) {
                if (!destroyed) {
                    runOnUiThread(() -> {
                        setRunningUi(false, null);
                        refreshLocalState();
                        String message = error.getMessage();
                        showStatus("تعذر إكمال التنزيل: "
                                + (message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message)
                                + "\nأي جزء تم تحميله محفوظ؛ تحقق من الإنترنت والمساحة ثم اختر الاستئناف.");
                    });
                }
            }
        });
    }

    private void downloadApk(ReleaseInfo info, File partialFile, File completeFile) throws Exception {
        if (info.size <= 0) {
            throw new IOException("تعذر معرفة الحجم الصحيح لملف ShadowBooks من GitHub.");
        }
        File directory = partialFile.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IOException("تعذر إنشاء مجلد التنزيل على الهاتف.");
        }

        long downloaded = partialFile.isFile() ? partialFile.length() : 0L;
        if (downloaded > info.size) {
            if (!partialFile.delete()) throw new IOException("تعذر حذف جزء سابق غير صالح.");
            downloaded = 0L;
        }
        if (directory != null) {
            long freeSpace = directory.getUsableSpace();
            long remaining = info.size - downloaded;
            if (freeSpace > 0 && freeSpace < remaining + 20L * 1024L * 1024L) {
                throw new IOException("لا توجد مساحة كافية لإكمال التنزيل. فرّغ مساحة على الهاتف ثم أعد المحاولة.");
            }
        }

        updateProgress(downloaded, info.size,
                downloaded > 0 ? "جارٍ استئناف التنزيل من الجزء المحفوظ" : "جارٍ تنزيل ShadowBooks على أجزاء قابلة للاستئناف");

        try (RandomAccessFile output = new RandomAccessFile(partialFile, "rw")) {
            if (output.length() > downloaded) output.setLength(downloaded);
            output.seek(downloaded);
            while (downloaded < info.size) {
                throwIfStopped();
                long end = Math.min(info.size - 1L, downloaded + CHUNK_SIZE - 1L);
                byte[] chunk = downloadRangeWithRetries(info.browserDownloadUrl, downloaded, end);
                output.write(chunk);
                output.getFD().sync();
                downloaded += chunk.length;
                updateProgress(downloaded, info.size, "جارٍ تنزيل ShadowBooks على أجزاء قابلة للاستئناف");
            }
        }

        if (partialFile.length() != info.size) {
            throw new IOException("حجم الملف المتنزل لا يطابق حجم ملف GitHub. أعد الاستئناف.");
        }
        if (info.sha256 != null) {
            updateProgress(info.size, info.size, "اكتمل التنزيل؛ جارٍ التحقق من سلامة الملف");
            String actualHash = sha256(partialFile);
            if (!info.sha256.equalsIgnoreCase(actualHash)) {
                if (!partialFile.delete()) {
                    throw new IOException("الملف لم يطابق بصمة GitHub، وتعذر حذف الجزء التالف.");
                }
                throw new IOException("الملف الذي تم تنزيله لم يطابق بصمة GitHub، لذا حُذف الجزء التالف. أعد التنزيل.");
            }
        }
        if (completeFile.exists() && !completeFile.delete()) {
            throw new IOException("تعذر استبدال ملف ShadowBooks سابق.");
        }
        if (!partialFile.renameTo(completeFile)) {
            throw new IOException("اكتمل التنزيل لكن تعذر تجهيز ملف التثبيت.");
        }
    }

    private byte[] downloadRangeWithRetries(String url, long start, long end) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_CHUNK_ATTEMPTS; attempt++) {
            throwIfStopped();
            try {
                return downloadRangeOnce(url, start, end);
            } catch (NonRetryableIOException error) {
                throw error;
            } catch (IOException error) {
                lastError = error;
                if (attempt == MAX_CHUNK_ATTEMPTS) break;
                sleepBeforeRetry(attempt);
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
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(30_000);
                connection.setReadTimeout(60_000);
                connection.setRequestProperty("User-Agent", "ShadowBooks-Downloader/1.0");
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
                    throw new NonRetryableIOException("رفض خادم التنزيل استئناف هذا الجزء (416). أعد فتح المنزّل والمحاولة.");
                }
                if (code != 206) {
                    throw new IOException("لم يقبل خادم التنزيل طلب الاستئناف (HTTP " + code + ").");
                }
                String contentRange = connection.getHeaderField("Content-Range");
                String expectedPrefix = "bytes " + start + "-";
                if (contentRange == null || !contentRange.startsWith(expectedPrefix)) {
                    throw new IOException("رجع جزء غير متوقع من خادم التنزيل.");
                }
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength)) {
                    byte[] buffer = new byte[32 * 1024];
                    int total = 0;
                    while (total < expectedLength) {
                        throwIfStopped();
                        int count = input.read(buffer, 0, Math.min(buffer.length, expectedLength - total));
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
                if (connection != null) connection.disconnect();
            }
        }
        throw new IOException("حدثت عمليات إعادة توجيه كثيرة أثناء تنزيل أحد الأجزاء.");
    }

    private ReleaseInfo fetchReleaseInfo() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API_URL).openConnection();
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "ShadowBooks-Downloader/1.0");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("تعذر جلب معلومات إصدار ShadowBooks من GitHub (HTTP " + code + ").");
            }
            String json = new String(readAll(connection.getInputStream()), StandardCharsets.UTF_8);
            int assetNameIndex = json.indexOf("\"" + APK_NAME + "\"");
            String scope = assetNameIndex >= 0
                    ? json.substring(assetNameIndex, Math.min(json.length(), assetNameIndex + 4096))
                    : json;
            String browserDownloadUrl = findString(scope, "\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
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

    private boolean isCompleteAndVerified(File completeFile, ReleaseInfo info) throws Exception {
        if (!completeFile.isFile() || info.size <= 0 || completeFile.length() != info.size) return false;
        return info.sha256 == null || info.sha256.equalsIgnoreCase(sha256(completeFile));
    }

    private void installDownloadedApk() {
        File completeFile = apkFile(false);
        if (!completeFile.isFile()) {
            refreshLocalState();
            showStatus("لم يتم العثور على ملف ShadowBooks المكتمل. اضغط تنزيل التطبيق من جديد.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            showStatus("اسمح لهذا المنزّل بتثبيت التطبيقات من إعدادات أندرويد، ثم عُد إلى هنا واضغط «تثبيت التطبيق».");
            refreshLocalState();
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".files", completeFile);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(install);
    }

    private File apkFile(boolean partial) {
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) directory = getFilesDir();
        return new File(directory, partial ? PARTIAL_NAME : APK_NAME);
    }

    private void refreshLocalState() {
        File completeFile = apkFile(false);
        File partialFile = apkFile(true);
        if (completeFile.isFile()) {
            progressBar.setProgress(1000);
            statusView.setText("تم تنزيل ShadowBooks والتحقق منه.\nالحجم: " + formatSize(completeFile.length()));
            actionButton.setText("تثبيت التطبيق");
        } else if (partialFile.isFile() && partialFile.length() > 0) {
            progressBar.setProgress(0);
            statusView.setText("يوجد جزء محفوظ من التنزيل: " + formatSize(partialFile.length())
                    + "\nاضغط استئناف التنزيل للإكمال من حيث توقف.");
            actionButton.setText("استئناف التنزيل");
        } else {
            progressBar.setProgress(0);
            statusView.setText("جاهز لتنزيل ShadowBooks بطريقة قابلة للاستئناف.");
            actionButton.setText("تنزيل التطبيق");
        }
    }

    private void setRunningUi(boolean value, String status) {
        running = value;
        actionButton.setEnabled(!value);
        actionButton.setText(value ? "جارٍ العمل؛ لا تغلق المنزّل" : actionButton.getText());
        if (status != null) statusView.setText(status);
    }

    private void showStatus(String text) {
        if (destroyed) return;
        runOnUiThread(() -> {
            if (!destroyed) statusView.setText(text);
        });
    }

    private void updateProgress(long downloaded, long total, String label) {
        if (destroyed) return;
        int progress = total > 0 ? (int) Math.min(1000L, downloaded * 1000L / total) : 0;
        double percent = total > 0 ? downloaded * 100.0 / total : 0.0;
        String text = label + "\n" + formatSize(downloaded) + " من " + formatSize(total)
                + String.format(Locale.US, " (%.1f%%)", percent);
        runOnUiThread(() -> {
            if (destroyed) return;
            progressBar.setProgress(progress);
            statusView.setText(text);
        });
    }

    private void throwIfStopped() throws InterruptedIOException {
        if (destroyed || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("توقف التنزيل.");
        }
    }

    private void sleepBeforeRetry(int attempt) throws InterruptedIOException {
        try {
            Thread.sleep(Math.min(15_000L, attempt * 2_000L));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("توقفت إعادة المحاولة.");
        }
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

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
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

    private static String formatSize(long bytes) {
        if (bytes < 1024L) return bytes + " بايت";
        double megabytes = bytes / 1_048_576.0;
        if (megabytes < 1024.0) return String.format(Locale.US, "%.1f ميغابايت", megabytes);
        return String.format(Locale.US, "%.2f غيغابايت", megabytes / 1024.0);
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    private static final class NonRetryableIOException extends IOException {
        NonRetryableIOException(String message) {
            super(message);
        }
    }
}
