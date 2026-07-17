package com.amigos.kinbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reminders + routine-chain engine (V2 §3, V2.2 §A). One state machine:
 * SCHEDULED → DELIVERED → ACKED | MISSED; routine steps chain off the previous
 * ack. Meds escalate to adherence, movement/hydration are invitation-only.
 *
 * Demo clock: routine inter-step delays are compressed to ~12s so a chain can
 * be shown live; daily reminders fire when the companion is open and due.
 */
public class ReminderEngine {

    public interface Callback {
        void speak(String text);
    }

    private static final long TICK_MS = 15_000;
    private static final long STEP_DELAY_DEMO_MS = 12_000;

    private static final class Reminder {
        String id;
        String type;
        String spoken;
        long ackWindowMin;
        String state; // SCHEDULED, DELIVERED, ACKED, MISSED
        long deliveredAt;
    }

    private final Context context;
    private final Callback callback;
    private final SharedPreferences state;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Reminder> reminders = new ArrayList<>();
    private Reminder awaitingAck;
    private TailoringRules tailoring;
    private boolean running;

    public ReminderEngine(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        this.state = context.getSharedPreferences("elder_state", Context.MODE_PRIVATE);
    }

    public void start() {
        loadReminders();
        running = true;
        handler.postDelayed(this::tick, 3000);
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void loadReminders() {
        try {
            JSONArray arr = new JSONArray(readAsset("v2/reminders.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.getString("reminder_id");
                r.type = o.getString("type");
                r.spoken = o.getString("spoken_template");
                r.ackWindowMin = o.optLong("ack_window_min", 45);
                r.state = state.getString(todayKey(r.id), "SCHEDULED");
                reminders.add(r);
            }
            tailoring = new TailoringRules(readAsset("v2/health_profile.json"));
        } catch (Exception e) {
            android.util.Log.w("ReminderEngine", "load failed", e);
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        for (Reminder r : reminders) {
            if ("SCHEDULED".equals(r.state)) {
                deliver(r);
                break; // one voice at a time
            }
        }
        if (awaitingAck != null
                && System.currentTimeMillis() > awaitingAck.deliveredAt + awaitingAck.ackWindowMin * 60_000L) {
            miss(awaitingAck);
            awaitingAck = null;
        }
        handler.postDelayed(this::tick, TICK_MS);
    }

    private void deliver(Reminder r) {
        r.state = "DELIVERED";
        r.deliveredAt = System.currentTimeMillis();
        persist(r);
        awaitingAck = r;
        callback.speak(r.spoken);
    }

    /** Called on every elder reply. Returns true if it acked a pending reminder. */
    public boolean onElderReply(String text) {
        if (awaitingAck == null) {
            return false;
        }
        String lower = text.toLowerCase();
        String[] ackWords = {"sudah", "sudah minum", "iya", "sudah dong", "yes", "done"};
        for (String word : ackWords) {
            if (lower.contains(word)) {
                Reminder r = awaitingAck;
                awaitingAck = null;
                r.state = "ACKED";
                persist(r);
                logAdherence(r, true);
                if ("medication".equals(r.type)) {
                    feedScoringEngine();
                    startRoutineChain();
                }
                return true;
            }
        }
        return false;
    }

    private void miss(Reminder r) {
        r.state = "MISSED";
        persist(r);
        logAdherence(r, false);
    }

    /** The clever bit (V2 §3.3): an ACKED med reminder writes tomorrow's T2 probe. */
    private void feedScoringEngine() {
        try {
            JSONArray custom = new JSONArray(state.getString("custom_facts_json", "[]"));
            for (int i = 0; i < custom.length(); i++) {
                if ("MED_RECALL_01".equals(custom.getJSONObject(i).optString("factId"))) {
                    return; // already generated
                }
            }
            JSONObject o = new JSONObject();
            o.put("factId", "MED_RECALL_01");
            o.put("tier", 2);
            o.put("category", "recent.health_routine");
            o.put("canonical", "obat tekanan darah");
            o.put("probe", "Obat apa yang kemarin pagi Ibu minum?");
            custom.put(o);
            state.edit().putString("custom_facts_json", custom.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Routine chain (V2.2 §A): med ack → movement → hydration, demo-compressed. */
    private void startRoutineChain() {
        try {
            JSONObject routine = new JSONObject(readAsset("v2/routines.json"));
            JSONArray steps = routine.getJSONArray("steps");
            scheduleStep(steps, 1);
        } catch (Exception ignored) {
        }
    }

    private void scheduleStep(JSONArray steps, int index) {
        if (index >= steps.length()) {
            return;
        }
        handler.postDelayed(() -> {
            try {
                JSONObject step = steps.getJSONObject(index);
                String prompt = step.getString("spoken_prompt");
                if ("movement".equals(step.optString("type")) && tailoring != null) {
                    prompt = tailoring.movementPrompt(prompt);
                }
                callback.speak(prompt);
                scheduleStep(steps, index + 1);
            } catch (Exception ignored) {
            }
        }, STEP_DELAY_DEMO_MS);
    }

    // ---- Adherence (dashboard reads this) ----

    private void logAdherence(Reminder r, boolean acked) {
        state.edit().putString(todayKey(r.id), acked ? "ACKED" : "MISSED").apply();
    }

    private String todayKey(String reminderId) {
        return "rem_" + reminderId + "_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private void persist(Reminder r) {
        state.edit().putString(todayKey(r.id), r.state).apply();
    }

    /** Dashboard: state for reminder on day-offset (0=today, 6=six days ago). */
    public static String adherenceFor(Context context, String reminderId, int dayOffset) {
        if (dayOffset > 0) {
            // Mock history for the demo grid — real days only exist from today on.
            int pattern = (reminderId.hashCode() + dayOffset) % 7;
            return pattern == 3 ? "MISSED" : "ACKED";
        }
        SharedPreferences state = context.getSharedPreferences("elder_state", Context.MODE_PRIVATE);
        String key = "rem_" + reminderId + "_"
                + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        return state.getString(key, "SCHEDULED");
    }

    public static List<String[]> reminderLabels(Context context) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(readAssetStatic(context, "v2/reminders.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new String[]{o.getString("reminder_id"), o.getString("label")});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String readAsset(String name) throws Exception {
        return readAssetStatic(context, name);
    }

    private static String readAssetStatic(Context context, String name) throws Exception {
        InputStream in = context.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
