package com.amigos.kinbridge;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.amigos.kinbridge.scoring.ScoringEngine;
import com.amigos.kinbridge.scoring.ScoringEngine.Escalation;
import com.amigos.kinbridge.scoring.ScoringEngine.Event;
import com.amigos.kinbridge.scoring.ScoringEngine.Verdict;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elder companion screen — typed chat first (hour-6 gate). Runs the real
 * Workflow Evaluation Agent loop: probe scheduler → judge → CRI/EWMA →
 * escalation, plus the V1 social-match engine (keyword → mock group).
 *
 * Demo Mode (demo_script.md): triple-tap Kenang's message to cycle the three
 * scripted scenarios — T1 miss → T2 warning → match found — each forced
 * through the real pipeline.
 */
public class CompanionActivity extends AppCompatActivity {

    private final ElderRepository repository = new ElderRepository();
    private final List<Event> sessionEvents = new ArrayList<>();
    private final Set<String> shownMatches = new HashSet<>();

    private ElderProfile profile;
    private ElderFact armedProbe;
    private Verdict forcedVerdict;
    private double forcedConfidence;
    private int turnsSinceProbe;
    private boolean probesDisabled;
    private boolean sessionClosed;

    private View root;
    private TextView companionText;
    private LinearLayout transcript;
    private ScrollView transcriptScroll;
    private EditText chatInput;

    // Demo Mode state
    private int demoTaps;
    private long firstDemoTapAt;
    private int demoScenario;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion);

        root = findViewById(android.R.id.content);
        companionText = findViewById(R.id.companionText);
        transcript = findViewById(R.id.transcript);
        transcriptScroll = findViewById(R.id.transcriptScroll);
        chatInput = findViewById(R.id.chatInput);

        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> onSend());
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSend();
                return true;
            }
            return false;
        });
        companionText.setOnClickListener(v -> onDemoTap());

        repository.loadProfile(this, new ElderRepository.ProfileCallback() {
            @Override
            public void onLoaded(ElderProfile loaded) {
                profile = loaded;
                companionSay(getString(R.string.companion_greeting));
            }

            @Override
            public void onError(String message) {
                companionSay(getString(R.string.companion_greeting));
            }
        });
    }

    // ---- Conversation loop (workflow spec §2 state machine) ----

    private void onSend() {
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty() || profile == null) {
            return;
        }
        chatInput.setText("");
        onElderTurn(text);
    }

    private void onElderTurn(String text) {
        addTranscript(true, text);
        scanForMatch(text);

        if (armedProbe != null) {
            evaluate(text, armedProbe);
        } else {
            turnsSinceProbe++;
            companionSay(getString(R.string.ack_neutral));
            maybeProbe();
        }
    }

    private void evaluate(String reply, ElderFact fact) {
        Verdict verdict;
        double confidence;
        if (forcedVerdict != null) {
            // Demo Mode forced verdict — still runs the real scoring pipeline.
            verdict = forcedVerdict;
            confidence = forcedConfidence;
            forcedVerdict = null;
        } else {
            verdict = judge(reply, fact);
            confidence = confidenceOf(verdict);
        }

        sessionEvents.add(new Event(fact.tier, verdict));
        repository.saveEvent(fact.tier, verdict.name().toLowerCase(),
                ScoringEngine.rawPoints(verdict, fact.tier),
                ScoringEngine.credit(verdict), eventLabel(verdict, fact));
        repository.markProbed(this, fact);
        armedProbe = null;
        turnsSinceProbe = 0;

        int t2Misses = 0;
        for (Event e : sessionEvents) {
            if (e.tier == 2 && e.verdict == Verdict.MISS) {
                t2Misses++;
            }
        }
        double sessionCri = sessionEvents.size() >= ScoringEngine.MIN_PROBES
                ? ScoringEngine.cri(sessionEvents) : 0;
        Escalation escalation = ScoringEngine.escalation(
                verdict, confidence, fact.tier, t2Misses, 0, sessionCri);

        switch (escalation) {
            case ACUTE:
                // Rule 1: companion pivot + family alert (spec §4, prompts.md §1)
                probesDisabled = true;
                repository.saveAlert("acute_t1",
                        getString(R.string.alert_acute_title),
                        getString(R.string.alert_acute_body));
                warmBackground();
                companionSay(getString(R.string.pivot_t1));
                return;
            case WARNING:
                repository.saveAlert("warning", getString(R.string.alert_warning_text), "");
                companionSay(getString(R.string.ack_warm));
                return;
            default:
                // Never correct her — warm ack and move on (prompts.md §1).
                companionSay(verdict == Verdict.EXACT
                        ? getString(R.string.ack_exact) : getString(R.string.ack_warm));
        }
    }

    private void maybeProbe() {
        if (probesDisabled || turnsSinceProbe < 3) {
            return;
        }
        // Scheduler (prompts.md §2): highest-priority tier first, cooldown-gated.
        long now = System.currentTimeMillis();
        ElderFact next = null;
        for (ElderFact f : profile.facts) {
            if (f.isEligible(now) && (next == null || f.tier < next.tier)) {
                next = f;
            }
        }
        if (next != null) {
            armedProbe = next;
            companionSay(next.probe());
        }
    }

    // ---- Social match engine (V1: keyword → mock group) ----

    private void scanForMatch(String elderText) {
        String text = elderText.toLowerCase();
        for (ElderProfile.CommunityGroup group : profile.groups) {
            if (shownMatches.contains(group.groupId)) {
                continue;
            }
            for (String keyword : group.keywords) {
                if (text.contains(keyword.toLowerCase())) {
                    shownMatches.add(group.groupId);
                    showMatchModal(group);
                    repository.saveMatchEvent(group.name + " — " + group.distanceKm + " km");
                    return;
                }
            }
        }
    }

    private void showMatchModal(ElderProfile.CommunityGroup group) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_match);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        ((TextView) dialog.findViewById(R.id.matchTitle)).setText(R.string.match_title);
        ((TextView) dialog.findViewById(R.id.matchGroupName)).setText(group.name);
        ((TextView) dialog.findViewById(R.id.matchDetails)).setText(
                getString(R.string.match_details, group.distanceKm, group.meets));
        dialog.findViewById(R.id.matchNotify).setOnClickListener(v -> {
            Toast.makeText(this, R.string.match_notified, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
        // Auto-dismiss after 12s (ui_spec.md §2 match-found behavior)
        handler.postDelayed(dialog::dismiss, 12_000);
    }

    // ---- Local judge placeholder (TODO: LLM judge via backend-held key) ----

    private Verdict judge(String reply, ElderFact fact) {
        String text = reply.toLowerCase();
        for (String value : fact.canonicalValues) {
            if (text.contains(value.toLowerCase())) {
                return Verdict.EXACT;
            }
        }
        for (String alias : fact.aliases) {
            if (!alias.isEmpty() && text.contains(alias.toLowerCase())) {
                return Verdict.EXACT;
            }
        }
        String[] missMarkers = {"tidak ingat", "lupa", "siapa ya", "entah",
                "tidak ada", "don't know", "not sure", "forgot", "can't remember"};
        for (String marker : missMarkers) {
            if (text.contains(marker)) {
                return Verdict.MISS;
            }
        }
        if (text.length() < 15 || text.contains("ya dia") || text.contains("hmm")
                || text.contains("maybe") || text.contains("mungkin")) {
            return Verdict.PARTIAL;
        }
        return Verdict.NO_ANSWER;
    }

    private double confidenceOf(Verdict verdict) {
        switch (verdict) {
            case EXACT:
                return 0.9;
            case MISS:
                return 0.9;
            case PARTIAL:
                return 0.7;
            default:
                return 0.6;
        }
    }

    // ---- Demo Mode: triple-tap cycles t1_miss → t2_warning → match_found ----

    private void onDemoTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - firstDemoTapAt > 1500) {
            demoTaps = 0;
            firstDemoTapAt = now;
        }
        demoTaps++;
        if (demoTaps >= 3 && profile != null) {
            demoTaps = 0;
            int scenario = demoScenario % 3;
            demoScenario++;
            switch (scenario) {
                case 0:
                    runT1MissScenario();
                    break;
                case 1:
                    runT2WarningScenario();
                    break;
                default:
                    runMatchScenario();
            }
        }
    }

    private void runT1MissScenario() {
        ElderFact fact = findFact("T1_SPOUSE_NAME");
        if (fact == null) {
            return;
        }
        armedProbe = fact;
        forcedVerdict = Verdict.MISS;
        forcedConfidence = 0.94;
        companionSay(fact.probe());
        handler.postDelayed(() ->
                onElderTurn("Suami saya... saya tidak ingat, siapa ya namanya?"), 900);
    }

    private void runT2WarningScenario() {
        // Two T2 misses seed the warning rule (demo_script.md §4)
        String[] t2Ids = {"T2_BREAKFAST_TODAY", "T2_LAST_FAMILY_VISIT"};
        runForcedMissChain(t2Ids, 0);
    }

    private void runForcedMissChain(String[] factIds, int index) {
        if (index >= factIds.length) {
            return;
        }
        ElderFact fact = findFact(factIds[index]);
        if (fact == null) {
            return;
        }
        armedProbe = fact;
        forcedVerdict = Verdict.MISS;
        forcedConfidence = 0.9;
        companionSay(fact.probe());
        handler.postDelayed(() -> {
            onElderTurn("Tidak ingat, Bu.");
            handler.postDelayed(() -> runForcedMissChain(factIds, index + 1), 900);
        }, 900);
    }

    private void runMatchScenario() {
        onElderTurn("Saya senang merawat anggrek di teras.");
    }

    private ElderFact findFact(String factId) {
        for (ElderFact f : profile.facts) {
            if (f.factId.equals(factId)) {
                return f;
            }
        }
        return null;
    }

    // ---- Tier-1 pivot visual: background warms (ui_spec.md §2) ----

    private void warmBackground() {
        int from = getColor(R.color.pure_white);
        int to = getColor(R.color.tint_wash);
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        animator.setDuration(2000);
        animator.addUpdateListener(a -> root.setBackgroundColor((int) a.getAnimatedValue()));
        animator.start();
    }

    // ---- Session close: CRI + EWMA trend point (spec §2 on_session_end) ----

    @Override
    protected void onStop() {
        super.onStop();
        if (!sessionClosed && profile != null
                && sessionEvents.size() >= ScoringEngine.MIN_PROBES) {
            sessionClosed = true;
            double cri = ScoringEngine.cri(sessionEvents);
            double ewma = ScoringEngine.ewma(cri, profile.prevEwma);
            repository.saveTrendPoint(cri, ewma, sessionEvents.size());
        }
    }

    // ---- UI helpers ----

    private void companionSay(String text) {
        companionText.setText(text);
        addTranscript(false, text);
    }

    private void addTranscript(boolean elder, String text) {
        TextView row = new TextView(this);
        row.setTextSize(15);
        row.setText(elder ? "Ibu: " + text : "Kenang: " + text);
        row.setTextColor(getColor(elder ? R.color.slate_gray : R.color.ink_black));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(lp);
        transcript.addView(row);
        transcriptScroll.post(() -> transcriptScroll.fullScroll(View.FOCUS_DOWN));
    }

    /** Humanized feed label from the fact's category (schema has no topic field). */
    private String eventLabel(Verdict verdict, ElderFact fact) {
        String topic = categoryLabel(fact.category);
        switch (verdict) {
            case EXACT:
                return (indonesian() ? "Mengingat " : "Remembered ") + topic;
            case PARTIAL:
                return (indonesian() ? "Ragu tentang " : "Hesitated on ") + topic;
            case MISS:
                return (indonesian() ? "Tidak dapat mengingat " : "Couldn't recall ") + topic;
            default:
                return getString(R.string.event_no_answer);
        }
    }

    private boolean indonesian() {
        String tags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                .toLanguageTags();
        return tags.startsWith("id") || tags.startsWith("in") || tags.isEmpty();
    }

    private String categoryLabel(String category) {
        boolean id = indonesian();
        Map<String, String[]> map = new HashMap<>();
        map.put("core_identity.family", new String[]{"memori keluarga inti", "a core family memory"});
        map.put("core_identity.location", new String[]{"alamat rumahnya", "her home address"});
        map.put("core_identity.self", new String[]{"tahun lahirnya", "her birth year"});
        map.put("recent.meals", new String[]{"santapan hari ini", "today's meal"});
        map.put("recent.family_events", new String[]{"acara keluarga terbaru", "a recent family event"});
        map.put("recent.health_routine", new String[]{"rutinitas obatnya", "her medication routine"});
        map.put("preferences.dining", new String[]{"restoran favoritnya", "her favorite restaurant"});
        map.put("preferences.music", new String[]{"lagu kesukaannya", "her favorite song"});
        map.put("preferences.hobbies", new String[]{"anggrek kesayangannya", "her beloved orchids"});
        map.put("preferences.entertainment", new String[]{"tontonan favoritnya", "her favorite show"});
        String[] labels = map.get(category);
        if (labels == null) {
            return id ? "sesuatu" : "something";
        }
        return id ? labels[0] : labels[1];
    }
}
