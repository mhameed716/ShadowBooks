package com.shadowbooks.downloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;

/**
 * After a phone restart, an unfinished download resumes by itself — the student
 * should never discover that 700 MB quietly vanished. A download the user paused
 * on purpose stays paused until they resume it manually.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences(DownloadService.PREFS, Context.MODE_PRIVATE);
            if (prefs.getBoolean("userPaused", false)) return;
            if ("complete".equals(prefs.getString("status", ""))) return;
            File part = DownloadService.partFile(context);
            File state = DownloadService.stateFile(context);
            if (part.isFile() && part.length() > 0 && state.isFile()) {
                DownloadService.start(context);
            }
        } catch (Exception ignored) {
            // If the system refuses a background start right after boot, opening
            // the downloader resumes the same partial file manually.
        }
    }
}
