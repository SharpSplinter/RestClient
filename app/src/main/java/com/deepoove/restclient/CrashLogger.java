package com.deepoove.restclient;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves uncaught Java crashes so they can be inspected without ADB. */
public final class CrashLogger {
    private static final String TAG = "RestClientCrash";
    private CrashLogger() {}

    public static void install(final Context context) {
        final Context appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable error) {
                try {
                    save(appContext, thread, error);
                } catch (Throwable loggingError) {
                    Log.e(TAG, "Could not save crash log", loggingError);
                } finally {
                    if (previous != null) {
                        previous.uncaughtException(thread, error);
                    } else {
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(10);
                    }
                }
            }
        });
    }

    private static void save(Context context, Thread thread, Throwable error) throws Exception {
        StringWriter trace = new StringWriter();
        PrintWriter printer = new PrintWriter(trace);
        printer.println("RestClient uncaught crash");
        printer.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()));
        printer.println("Thread: " + thread.getName());
        printer.println("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        printer.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        error.printStackTrace(printer);
        printer.flush();
        byte[] bytes = trace.toString().getBytes("UTF-8");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "RestClient-crash.log");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                    if (out != null) out.write(bytes);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
                return;
            }
        }

        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(dir, "RestClient-crash.log")), "UTF-8")) {
            out.write(trace.toString());
        }
    }
}
