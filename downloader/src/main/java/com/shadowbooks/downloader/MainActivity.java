package com.shadowbooks.downloader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thin UI over DownloadService: the transfer itself lives in the foreground
 * service, so closing this screen never cancels the download. The screen polls
 * the service's published state, offers pause/resume, and installs the APK from
 * the phone's public Downloads folder once it is verified complete.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 7;
    private static final int REQUEST_STORAGE = 8;
    private static final String MAIN_PACKAGE = "com.shadowbooks";
    private static final String RELEASE_API_URL =
            "https://api.github.com/repos/mhameed716/ShadowBooks/releases/tags/v1.0";
    /** latest = installed bytes ARE the published release; update = a newer
     *  release exists; none = ShadowBooks is not installed; unknown = the
     *  check has not finished (or the network was down). */
    private volatile String versionVerdict = "unknown";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 700);
        }
    };

    private SharedPreferences prefs;
    private TextView statusView;
    private ProgressBar progressBar;
    private Button actionButton;
    private Button pauseButton;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(DownloadService.PREFS, MODE_PRIVATE);

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
        intro.setText("هذا المنزّل الصغير يحمّل تطبيق ShadowBooks بسرعة عالية "
                + "بواسطة عدة اتصالات متوازية، ويعمل في الخلفية حتى لو أُغلقت الشاشة. "
                + "إذا انقطع الإنترنت أو أُعيد تشغيل الهاتف، يُستأنف التنزيل تلقائيًا "
                + "من حيث توقف دون البدء من الصفر. وعند الاكتمال يُحفظ التطبيق "
                + "في مجلد التنزيلات (Downloads) على هاتفك جاهزًا للتثبيت.");
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

        pauseButton = new Button(this);
        pauseButton.setTextSize(16);
        pauseButton.setText("إيقاف مؤقت");
        pauseButton.setOnClickListener(view -> pauseDownload());
        layout.addView(pauseButton, matchWrap(dp(12)));

        deleteButton = new Button(this);
        deleteButton.setTextSize(14);
        deleteButton.setText("حذف الجزء المنزّل والبدء من جديد");
        deleteButton.setOnClickListener(view -> deletePartial());
        layout.addView(deleteButton, matchWrap(dp(8)));

        TextView note = new TextView(this);
        note.setText("حجم التطبيق نحو 120 ميغابايت، وبعد تثبيته يمكنك تنزيل الكتب "
                + "والملخصات والنماذج التي تحتاجها فقط من تبويب «المتجر» داخله. "
                + "اترك مساحة كافية على الهاتف، ويفضل استخدام شبكة Wi-Fi مستقرة.");
        note.setTextSize(14);
        note.setGravity(Gravity.CENTER);
        layout.addView(note, matchWrap(dp(22)));

        setContentView(layout);

        // Opening the downloader with an unfinished, un-paused download resumes it
        // automatically (for example after the phone restarted and the system did
        // not revive the service).
        File part = DownloadService.partFile(this);
        boolean heartbeatFresh =
                System.currentTimeMillis() - prefs.getLong("heartbeat", 0) < 4000;
        if (part.isFile() && part.length() > 0
                && !prefs.getBoolean("userPaused", false)
                && !"complete".equals(prefs.getString("status", ""))
                && !heartbeatFresh) {
            DownloadService.start(this);
        }
        checkVersionsAsync();
        render();
    }

    /**
     * Version sense, checked off the UI thread: if ShadowBooks is installed,
     * hash its APK on the phone and compare it with the fingerprint (SHA-256)
     * GitHub publishes for the release asset. Identical bytes = the very
     * latest version: no download, no reinstall. Different bytes = an old
     * copy: the downloader fetches the new release (the service already
     * refuses to mix bytes from two builds, so the new download cannot fail
     * by resuming an old partial).
     */
    private void checkVersionsAsync() {
        new Thread(() -> {
            String verdict = "unknown";
            try {
                android.content.pm.PackageInfo installed = installedMainApp();
                if (installed == null) {
                    verdict = "none";
                } else {
                    String digest = fetchReleaseDigest();
                    if (digest != null) {
                        String installedDigest = sha256Of(
                                new File(installed.applicationInfo.sourceDir));
                        verdict = digest.equalsIgnoreCase(installedDigest)
                                ? "latest" : "update";
                    }
                }
            } catch (Exception ignored) {
                // Network down or package vanished: keep the neutral UI.
            }
            versionVerdict = verdict;
            handler.post(this::render);
        }, "sb-version-check").start();
    }

    private android.content.pm.PackageInfo installedMainApp() {
        try {
            return getPackageManager().getPackageInfo(MAIN_PACKAGE, 0);
        } catch (Exception error) {
            return null;
        }
    }

    private String fetchReleaseDigest() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API_URL).openConnection();
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(20_000);
            connection.setRequestProperty("User-Agent", "ShadowBooks-Downloader/2.0");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            if (connection.getResponseCode() / 100 != 2) return null;
            String json;
            try (InputStream input = connection.getInputStream()) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);
                json = buffer.toString(StandardCharsets.UTF_8.name());
            }
            int asset = json.indexOf("\"ShadowBooks.apk\"");
            String scope = asset >= 0
                    ? json.substring(asset, Math.min(json.length(), asset + 4096)) : json;
            Matcher matcher = Pattern
                    .compile("\"digest\"\\s*:\\s*\"sha256:([0-9a-fA-F]{64})\"")
                    .matcher(scope);
            return matcher.find() ? matcher.group(1) : null;
        } catch (Exception error) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String sha256Of(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] chunk = new byte[64 * 1024];
            int read;
            while ((read = input.read(chunk)) != -1) digest.update(chunk, 0, read);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }

    private void openMainApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(MAIN_PACKAGE);
        if (launch != null) {
            startActivity(launch);
        } else {
            statusView.setText("ShadowBooks مثبّت لديك بأحدث إصدار. افتحه من شاشة هاتفك الرئيسية.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(poll);
        super.onPause();
    }

    private void onActionClicked() {
        if ("latest".equals(versionVerdict)) {
            openMainApp();
        } else if (completeApkExists()) {
            installDownloadedApk();
        } else if (!isServiceActive()) {
            startDownload();
        }
    }

    private void startDownload() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE);
        }
        prefs.edit().putBoolean("userPaused", false).apply();
        DownloadService.start(this);
        render();
    }

    private void pauseDownload() {
        prefs.edit().putBoolean("userPaused", true).apply();
        try {
            startService(new Intent(this, DownloadService.class)
                    .setAction(DownloadService.ACTION_PAUSE));
        } catch (Exception ignored) {
            // The service is not running; the persisted userPaused flag is enough.
        }
        render();
    }

    private void deletePartial() {
        if (isServiceActive()) return;
        //noinspection ResultOfMethodCallIgnored
        DownloadService.partFile(this).delete();
        //noinspection ResultOfMethodCallIgnored
        DownloadService.stateFile(this).delete();
        prefs.edit()
                .putString("status", "idle")
                .putLong("downloaded", 0)
                .putLong("total", 0)
                .putLong("speed", 0)
                .putString("completeUri", "")
                .putString("message", "جاهز لتنزيل ShadowBooks.")
                .apply();
        render();
    }

    private boolean isServiceActive() {
        String status = prefs.getString("status", "idle");
        boolean activeStatus = "running".equals(status) || "waiting".equals(status)
                || "verifying".equals(status) || "publishing".equals(status);
        boolean heartbeatFresh =
                System.currentTimeMillis() - prefs.getLong("heartbeat", 0) < 5000;
        return activeStatus && heartbeatFresh;
    }

    private void render() {
        String status = prefs.getString("status", "idle");
        String message = prefs.getString("message", "");
        long downloaded = prefs.getLong("downloaded", 0);
        long total = prefs.getLong("total", 0);
        long speed = prefs.getLong("speed", 0);
        boolean active = isServiceActive();
        boolean complete = completeApkExists();
        File part = DownloadService.partFile(this);
        boolean hasPartial = part.isFile() && part.length() > 0;

        if ("latest".equals(versionVerdict) && !active) {
            progressBar.setProgress(1000);
            statusView.setText("ShadowBooks مثبّت على هاتفك بأحدث إصدار متاح — "
                    + "لا حاجة لتنزيله من جديد.");
            actionButton.setText("فتح ShadowBooks");
            actionButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
            return;
        }

        if ("update".equals(versionVerdict) && !active && !complete) {
            statusView.setText("لديك نسخة أقدم من ShadowBooks، ويتوفر إصدار جديد. "
                    + "اضغط الزر لتنزيل النسخة الجديدة المطلوبة بالضبط "
                    + "(لن تختلط ببقايا النسخة القديمة).");
            actionButton.setText("تنزيل الإصدار الجديد");
            actionButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            deleteButton.setVisibility(hasPartial ? View.VISIBLE : View.GONE);
            progressBar.setProgress(hasPartial && total > 0
                    ? (int) Math.min(1000L, downloaded * 1000L / total) : 0);
            return;
        }

        if (complete) {
            progressBar.setProgress(1000);
            long size = total > 0 ? total : publishedApkSize();
            statusView.setText("تم تنزيل ShadowBooks والتحقق منه، وهو محفوظ في مجلد "
                    + "التنزيلات (Downloads) باسم ShadowBooks.apk."
                    + (size > 0 ? "\nالحجم: " + DownloadService.formatSize(size) : ""));
            actionButton.setText("تثبيت التطبيق");
            actionButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
            return;
        }
        if ("complete".equals(status)) {
            // Marked complete but the file was removed from Downloads afterwards.
            prefs.edit().putString("status", "idle").putString("completeUri", "").apply();
            status = "idle";
        }

        progressBar.setProgress(total > 0
                ? (int) Math.min(1000L, downloaded * 1000L / total) : 0);

        if (active) {
            statusView.setText(message != null && !message.isEmpty()
                    ? message : "جارٍ تنزيل ShadowBooks...");
            actionButton.setText("جارٍ التنزيل في الخلفية...");
            actionButton.setEnabled(false);
            pauseButton.setVisibility(View.VISIBLE);
            pauseButton.setEnabled(true);
            deleteButton.setVisibility(View.GONE);
        } else if ("error".equals(status) || "paused".equals(status)) {
            statusView.setText(message != null && !message.isEmpty()
                    ? message : "التنزيل متوقف. الجزء المنزّل محفوظ ويمكن استئنافه.");
            actionButton.setText(hasPartial ? "استئناف التنزيل" : "تنزيل التطبيق");
            actionButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            deleteButton.setVisibility(hasPartial ? View.VISIBLE : View.GONE);
        } else if (hasPartial) {
            statusView.setText("يوجد جزء محفوظ من التنزيل: "
                    + DownloadService.formatSize(part.length())
                    + "\nاضغط استئناف التنزيل للإكمال من حيث توقف.");
            actionButton.setText("استئناف التنزيل");
            actionButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.VISIBLE);
        } else {
            statusView.setText("جاهز لتنزيل ShadowBooks بسرعة عالية وبطريقة "
                    + "قابلة للاستئناف التلقائي.");
            actionButton.setText("تنزيل التطبيق");
            actionButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
        }
        if (speed > 0 && active) {
            statusView.setText(statusView.getText() + "\n("
                    + DownloadService.formatSize(speed) + "/ث)");
        }
    }

    private boolean completeApkExists() {
        String uriText = prefs.getString("completeUri", "");
        if (uriText != null && !uriText.isEmpty()) {
            try (Cursor cursor = getContentResolver().query(
                    Uri.parse(uriText), new String[]{"_id"}, null, null, null)) {
                return cursor != null && cursor.moveToFirst();
            } catch (Exception error) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT < 29) {
            File published = DownloadService.publicApkFile();
            return published.isFile() && published.length() > 0;
        }
        return false;
    }

    private long publishedApkSize() {
        if (Build.VERSION.SDK_INT < 29) {
            File published = DownloadService.publicApkFile();
            if (published.isFile()) return published.length();
        }
        return 0;
    }

    private void installDownloadedApk() {
        if (!completeApkExists()) {
            prefs.edit().putString("status", "idle").putString("completeUri", "").apply();
            render();
            statusView.setText("لم يتم العثور على ملف ShadowBooks في مجلد التنزيلات. "
                    + "اضغط تنزيل التطبيق من جديد.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            statusView.setText("اسمح لهذا المنزّل بتثبيت التطبيقات من إعدادات أندرويد، "
                    + "ثم عُد إلى هنا واضغط «تثبيت التطبيق».");
            return;
        }
        Uri apkUri;
        String uriText = prefs.getString("completeUri", "");
        if (uriText != null && !uriText.isEmpty()) {
            apkUri = Uri.parse(uriText);
        } else {
            apkUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".files", DownloadService.publicApkFile());
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(install);
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
}
