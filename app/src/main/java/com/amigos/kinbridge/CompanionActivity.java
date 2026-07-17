package com.amigos.kinbridge;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.amigos.kinbridge.scoring.ScoringEngine;
import com.amigos.kinbridge.scoring.ScoringEngine.Escalation;
import com.amigos.kinbridge.scoring.ScoringEngine.Event;
import com.amigos.kinbridge.scoring.ScoringEngine.Verdict;

import java.util.ArrayList;
import java.util.List;

/**
 * Elder companion screen — typed chat first (system_architecture.md hour-6
 * gate: the pipeline must score correctly before voice is added).
 *
 * Runs the real Workflow Evaluation Agent loop (workflow_agent_spec.md §2):
 * probe scheduler → judge → CRI/EWMA scoring → escalation. The judge is a
 * local alias/keyword stand-in — TODO: swap for the gpt-4o-mini structured
 * call (prompts.md §3) via a Cloud Function that holds the OpenAI key.
 */
public class CompanionActivity extends AppCompatActivity {

    private final ElderRepository repository = new ElderRepository();
    private final List<Event> sessionEvents = new ArrayList<>();

    private ElderProfile profile;
    private ElderFact armedProbe;
    private Verdict forcedVerdict;
    private double forcedConfidence;
    private int turnsSinceProbe;
    private boolean probesDisabled;
    private boolean sessionClosed;
    private boolean indonesian = true;

    private TextView companionText;
    private LinearLayout transcript;
    private ScrollView transcriptScroll;
    private EditText chatInput;

    // Demo trigger: triple-tap the companion message (demo_script.md §Demo Mode)
    private int demoTaps;
    private long firstDemoTapAt;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion);

        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        indonesian = tags.startsWith("id") || tags.startsWith("in") || tags.isEmpty();

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

        repository.ensureSeeded(() -> repository.loadProfile(new ElderRepository.ProfileCallback() {
            @Override
            public void onLoaded(ElderProfile loaded) {
                profile = loaded;
                companionSay(getString(R.string.companion_greeting));
            }

            @Override
            public void onError(String message) {
                companionSay(getString(R.string.companion_greeting));
            }
        }));
    }

    // ---- Conversation loop (workflow spec §2 state machine) ----

    private void onSend() {
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty() || profile == null) {
            return;
        }
        chatInput.setText("");
        addTranscript(true, text);

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
            Verdict judged = judge(reply, fact);
            verdict = judged;
            confidence = confidenceOf(judged);
        }

        sessionEvents.add(new Event(fact.tier, verdict));
        repository.saveEvent(fact.tier, verdict.name().toLowerCase(),
                ScoringEngine.rawPoints(verdict, fact.tier),
                ScoringEngine.credit(verdict), eventLabel(verdict, fact));

        fact.lastProbedAt = System.currentTimeMillis();
        repository.saveFacts(profile.facts);
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
                companionSay(getString(R.string.pivot_t1));
                return;
            case WARNING:
                repository.saveAlert("warning", getString(R.string.alert_warning_text), "");
                companionSay(getString(R.string.ack_warm));
                return;
            default:
                companionSay(verdict == Verdict.EXACT
                        ? getString(R.string.ack_exact) : getString(R.string.ack_warm));
        }
        // A Tier-1 miss below confidence, or anything non-acute: never correct
        // her, respond warmly and move on (prompts.md §1 MEMORY PROBES).
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
            companionSay(indonesian ? next.questionId : next.questionEn);
        }
    }

    // ---- Local judge placeholder (TODO: LLM judge via Cloud Function) ----

    private Verdict judge(String reply, ElderFact fact) {
        String text = reply.toLowerCase();
        if (text.contains(fact.canonical.toLowerCase())) {
            return Verdict.EXACT;
        }
        for (String alias : fact.aliases) {
            if (text.contains(alias.toLowerCase())) {
                return Verdict.EXACT;
            }
        }
        String[] missMarkers = {"tidak ingat", "lupa", "siapa ya", "entah",
                "don't know", "not sure", "forgot", "can't remember", "dont know"};
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

    // ---- Demo Mode (demo_script.md): triple-tap forces a scripted T1 miss ----

    private void onDemoTap() {
        long now = SystemClock.elapsedRealtime();
        if (now - firstDemoTapAt > 1500) {
            demoTaps = 0;
            firstDemoTapAt = now;
        }
        demoTaps++;
        if (demoTaps >= 3 && profile != null) {
            demoTaps = 0;
            for (ElderFact f : profile.facts) {
                if (f.tier == 1) {
                    armedProbe = f;
                    forcedVerdict = Verdict.MISS;
                    forcedConfidence = 0.94;
                    companionSay(indonesian ? f.questionId : f.questionEn);
                    // Scripted elder reply runs through the real pipeline.
                    chatInput.postDelayed(() -> {
                        addTranscript(true, "Saya tidak ingat... siapa ya?");
                        evaluate("Saya tidak ingat... siapa ya?", f);
                    }, 900);
                    return;
                }
            }
        }
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

    private String eventLabel(Verdict verdict, ElderFact fact) {
        String topic = indonesian ? fact.topicId : fact.topicEn;
        switch (verdict) {
            case EXACT:
                return (indonesian ? "Mengingat " : "Remembered ") + topic;
            case PARTIAL:
                return (indonesian ? "Ragu tentang " : "Hesitated on ") + topic;
            case MISS:
                return (indonesian ? "Tidak dapat mengingat " : "Couldn't recall ") + topic;
            default:
                return getString(R.string.event_no_answer);
        }
    }
}
