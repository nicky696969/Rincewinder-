package com.nicky.rincewinder.agent;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Log na kartu (8.3 název RINCEAGT.LOG v kořeni karty, jako TRAPFOC.LOG) + logcat, tag RinceAgent.
 * Zápis nikdy nesmí shodit aplikaci; chyba se hlásí do logcatu.
 */
public final class FileLog implements Sink {

    private static final String TAG = "RinceAgent";
    private static final String NAME = "RINCEAGT.LOG";
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final FileLog INSTANCE = new FileLog();

    private File file;
    private volatile String last = "";
    private volatile String state = "log: ?";
    private boolean errorReported;

    private FileLog() {
    }

    public static FileLog get() {
        return INSTANCE;
    }

    public synchronized void init() {
        if (file != null) {
            return;
        }
        try {
            File dir = Environment.getExternalStorageDirectory();
            file = new File(dir, NAME);
            log("=== start === dir=" + dir + " exists=" + dir.exists() + " canWrite=" + dir.canWrite());
        } catch (Throwable t) {
            file = null;
            state = "log: INIT FAIL";
            Log.e(TAG, "log init", t);
        }
    }

    public synchronized void log(String s) {
        Log.i(TAG, s);
        last = s;
        if (file == null) {
            return;
        }
        try {
            FileWriter w = new FileWriter(file, true);
            w.write(FMT.format(new Date()) + " " + s + "\n");
            w.close();
            state = "log: OK";
        } catch (Throwable t) {
            state = "log: FAIL " + t.getClass().getSimpleName();
            if (!errorReported) {
                errorReported = true;
                Log.e(TAG, "log write failed: " + file, t);
            }
        }
    }

    public String last() {
        return last;
    }

    public String state() {
        return state;
    }
}
