package com.amigos.kinbridge;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Per-day activity log: one plain-text file per calendar day, created on the
 * day's first conversation turn and appended to until the day is over (the
 * filename is recomputed on every write, so a new file starts at midnight).
 * Files live in app-private storage — filesDir/daily_logs/activity_yyyy-MM-dd.txt —
 * and feed the future memory/life-history feature.
 */
public class DailyLogStore {

    private static final String DIR = "daily_logs";

    private String elderName;

    public DailyLogStore(String elderName) {
        this.elderName = elderName;
    }

    public void setElderName(String name) {
        this.elderName = name;
    }

    /** Appends one conversation turn: "[HH:mm] Ibu: …" / "[HH:mm] Kenang: …". */
    public synchronized void appendTurn(Context context, boolean elder, String text) {
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        append(context, formatLine(time, elder ? elderName : "Kenang", text) + "\n");
    }

    /** Appends a diary-style narrative section generated from the day so far. */
    public synchronized void appendSummary(Context context, String summary) {
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
        append(context, "\n--- Ringkasan aktivitas (" + time + ") ---\n" + summary + "\n");
    }

    /** Appends a dictated diary entry, stamped with full date + time. */
    public synchronized void appendDiaryEntry(Context context, String entry) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        append(context, formatDiarySection(stamp, entry) + "\n");
    }

    /** Full text of today's file, or "" if nothing was logged yet. */
    public String readToday(Context context) {
        File file = todayFile(context);
        if (!file.exists()) {
            return "";
        }
        try {
            FileInputStream in = new FileInputStream(file);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private void append(Context context, String text) {
        try {
            File file = todayFile(context);
            boolean newDay = !file.exists();
            FileOutputStream out = new FileOutputStream(file, true);
            if (newDay) {
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                out.write(("=== Aktivitas " + elderName + " — " + date + " ===\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception e) {
            android.util.Log.w("DailyLogStore", "append failed", e);
        }
    }

    private File todayFile(Context context) {
        return new File(new File(context.getFilesDir(), DIR), fileNameFor(new Date()));
    }

    // ---- Pure helpers (JVM-testable) ----

    static String fileNameFor(Date date) {
        return "activity_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date) + ".txt";
    }

    static String formatLine(String time, String speaker, String text) {
        return "[" + time + "] " + speaker + ": " + text;
    }
}
