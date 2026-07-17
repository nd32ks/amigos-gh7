package com.amigos.kinbridge;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Komorbid intake as a GuidedQuestion feed (V2.2 §C): one spoken question per
 * screen, large buttons, and the universal escape hatches — "Tidak tahu"
 * never blocks, "Minta tolong Dewi" delegates a task card to the guardian.
 */
public class GuidedQuestionActivity extends AppCompatActivity {

    private final ElderRepository repository = new ElderRepository();

    private static final int[] QUESTIONS = {
            R.string.guided_q_hypertension,
            R.string.guided_q_diabetes,
            R.string.guided_q_knee,
            R.string.guided_q_seafood,
    };
    private static final String[] QUESTION_KEYS = {
            "komorbid.hipertensi", "komorbid.diabetes", "komorbid.lutut", "komorbid.seafood",
    };

    private int index;
    private TextToSpeech tts;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guided_question);

        tts = new TextToSpeech(this, status -> speakCurrent());

        findViewById(R.id.guidedYes).setOnClickListener(v -> answer("ya"));
        findViewById(R.id.guidedNo).setOnClickListener(v -> answer("tidak"));
        findViewById(R.id.guidedUnknown).setOnClickListener(v -> answer("tidak_tahu"));
        findViewById(R.id.guidedDelegate).setOnClickListener(v -> delegate());

        showQuestion();
    }

    private void showQuestion() {
        if (index >= QUESTIONS.length) {
            ((TextView) findViewById(R.id.guidedQuestion)).setText(R.string.guided_done);
            findViewById(R.id.guidedYes).setVisibility(android.view.View.GONE);
            findViewById(R.id.guidedNo).setVisibility(android.view.View.GONE);
            findViewById(R.id.guidedUnknown).setVisibility(android.view.View.GONE);
            findViewById(R.id.guidedDelegate).setVisibility(android.view.View.GONE);
            findViewById(R.id.guidedQuestion).postDelayed(this::finish, 1800);
            return;
        }
        ((TextView) findViewById(R.id.guidedQuestion)).setText(QUESTIONS[index]);
        speakCurrent();
    }

    private void speakCurrent() {
        if (tts != null && index < QUESTIONS.length) {
            tts.speak(getString(QUESTIONS[index]), TextToSpeech.QUEUE_FLUSH, null, "gq_" + index);
        }
    }

    private void answer(String value) {
        getSharedPreferences("elder_state", MODE_PRIVATE).edit()
                .putString("health_answer_" + QUESTION_KEYS[index], value).apply();
        ((TextView) findViewById(R.id.guidedQuestion)).setText(R.string.guided_thanks);
        nextAfter(900);
    }

    private void delegate() {
        // V2.2 §D: delegation card pops on the guardian dashboard.
        repository.saveDelegation("question", getString(QUESTIONS[index]),
                "Tanya Dewi saja ya.");
        ((TextView) findViewById(R.id.guidedQuestion)).setText(R.string.guided_delegated);
        nextAfter(1200);
    }

    private void nextAfter(long delayMs) {
        findViewById(R.id.guidedQuestion).postDelayed(() -> {
            index++;
            showQuestion();
        }, delayMs);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
