package com.amigos.kinbridge;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * First-run onboarding, shown once before login:
 *   step 1 — language, with a rotating multilingual greeting (Apple-style)
 *   step 2 — role, select a card then Continue (Back returns to step 1)
 * Choices persist in SharedPreferences; every transition is a soft fade.
 */
public class OnboardingActivity extends AppCompatActivity {

    static final String PREFS = "kinbridge_prefs";
    static final String KEY_ONBOARDED = "onboarding_done";
    static final String KEY_ROLE = "user_role";
    static final String KEY_STEP = "onboarding_step";
    static final String ROLE_SENIOR = "senior";
    static final String ROLE_VOLUNTEER = "volunteer";

    private static final long FADE_MS = 150;
    private static final long GREETING_SWAP_MS = 120;
    private static final long GREETING_PERIOD_MS = 1800;

    /** Greeting + companion prompt, one pair per language. */
    private static final String[][] GREETINGS = {
            {"Selamat datang!", "Bahasa apa yang ingin Anda gunakan?"},
            {"Welcome!", "What language would you like to speak?"},
            {"ようこそ！", "どの言語を使いますか？"},
            {"¡Bienvenido!", "¿Qué idioma te gustaría hablar?"},
            {"Bienvenue !", "Quelle langue souhaitez-vous parler ?"},
            {"Willkommen!", "Welche Sprache möchtest du sprechen?"},
            {"환영합니다!", "어떤 언어를 사용하시겠어요?"},
            {"欢迎！", "您想使用哪种语言？"},
    };

    /** Set before the locale-switch recreate so the new instance fades back in. */
    private static boolean pendingFadeIn;

    private SharedPreferences prefs;
    private boolean transitioning;

    private final Handler greetingHandler = new Handler(Looper.getMainLooper());
    private int greetingIndex;
    private final Runnable greetingTick = new Runnable() {
        @Override
        public void run() {
            cycleGreeting();
            greetingHandler.postDelayed(this, GREETING_PERIOD_MS);
        }
    };

    private String selectedRole;
    private View optionSenior;
    private View optionVolunteer;
    private Button continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ONBOARDED, false)) {
            // Onboarding already done — go straight to login without showing UI.
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        optionSenior = findViewById(R.id.optionSenior);
        optionVolunteer = findViewById(R.id.optionVolunteer);
        continueButton = findViewById(R.id.continueButton);

        int step = prefs.getInt(KEY_STEP, 1);
        findViewById(R.id.stepLanguage).setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.stepRole).setVisibility(step == 2 ? View.VISIBLE : View.GONE);

        findViewById(R.id.optionIndonesian).setOnClickListener(v -> chooseLanguage("id"));
        findViewById(R.id.optionEnglish).setOnClickListener(v -> chooseLanguage("en"));
        optionSenior.setOnClickListener(v -> selectRole(ROLE_SENIOR));
        optionVolunteer.setOnClickListener(v -> selectRole(ROLE_VOLUNTEER));
        continueButton.setOnClickListener(v -> chooseRole());
        findViewById(R.id.backToLanguage).setOnClickListener(v -> backToLanguageStep());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (findViewById(R.id.stepRole).getVisibility() == View.VISIBLE) {
                    backToLanguageStep();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (pendingFadeIn) {
            pendingFadeIn = false;
            View content = findViewById(android.R.id.content);
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(FADE_MS).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        View stepLanguage = findViewById(R.id.stepLanguage);
        if (stepLanguage != null && stepLanguage.getVisibility() == View.VISIBLE) {
            greetingHandler.removeCallbacks(greetingTick);
            greetingHandler.postDelayed(greetingTick, GREETING_PERIOD_MS);
        }
    }

    @Override
    protected void onPause() {
        greetingHandler.removeCallbacks(greetingTick);
        super.onPause();
    }

    // ---- Step 1: rotating greeting ----

    private void cycleGreeting() {
        greetingIndex = (greetingIndex + 1) % GREETINGS.length;
        String[] pair = GREETINGS[greetingIndex];
        fadeSwap(findViewById(R.id.greetingText), pair[0]);
        fadeSwap(findViewById(R.id.promptText), pair[1]);
    }

    private void fadeSwap(TextView view, String text) {
        view.animate().alpha(0f).setDuration(GREETING_SWAP_MS).withEndAction(() -> {
            view.setText(text);
            view.animate().alpha(1f).setDuration(GREETING_SWAP_MS).start();
        }).start();
    }

    // ---- Step 2: role selection ----

    private void selectRole(String role) {
        if (transitioning) {
            return;
        }
        selectedRole = role;
        optionSenior.setBackgroundResource(
                ROLE_SENIOR.equals(role) ? R.drawable.bg_card_selected : R.drawable.bg_card_ripple);
        optionVolunteer.setBackgroundResource(
                ROLE_VOLUNTEER.equals(role) ? R.drawable.bg_card_selected : R.drawable.bg_card_ripple);
        if (!continueButton.isClickable()) {
            continueButton.setClickable(true);
            continueButton.animate().alpha(1f).setDuration(FADE_MS).start();
        }
    }

    private void backToLanguageStep() {
        prefs.edit().putInt(KEY_STEP, 1).apply();

        // Reset the role selection so a re-entry starts clean.
        selectedRole = null;
        optionSenior.setBackgroundResource(R.drawable.bg_card_ripple);
        optionVolunteer.setBackgroundResource(R.drawable.bg_card_ripple);
        continueButton.setClickable(false);
        continueButton.setAlpha(0.35f);

        findViewById(R.id.stepRole).setVisibility(View.GONE);
        View stepLanguage = findViewById(R.id.stepLanguage);
        stepLanguage.setAlpha(0f);
        stepLanguage.setVisibility(View.VISIBLE);
        stepLanguage.animate().alpha(1f).setDuration(FADE_MS).start();
        greetingHandler.removeCallbacks(greetingTick);
        greetingHandler.postDelayed(greetingTick, GREETING_PERIOD_MS);
    }

    // ---- Flow transitions ----

    private void chooseLanguage(String languageTag) {
        if (transitioning) {
            return;
        }
        transitioning = true;
        prefs.edit().putInt(KEY_STEP, 2).apply();
        pendingFadeIn = true;
        fadeOut(() -> {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    private void chooseRole() {
        if (transitioning || selectedRole == null) {
            return;
        }
        transitioning = true;
        prefs.edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_ROLE, selectedRole)
                .putInt(KEY_STEP, 1)
                .apply();
        fadeOut(() -> {
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void fadeOut(Runnable after) {
        findViewById(android.R.id.content).animate()
                .alpha(0f).setDuration(FADE_MS)
                .withEndAction(after).start();
    }
}
