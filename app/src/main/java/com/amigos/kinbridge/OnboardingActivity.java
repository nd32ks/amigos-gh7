package com.amigos.kinbridge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * First-run flow (MAIN_WORKFLOW STOP FLAG — mock role-select, zero credentials):
 *   step 1 — language, Indonesian/English rotating greeting
 *   step 2 — role-select: Ibu Sri (elder) · Dewi (guardian) · Sari (social worker)
 *            — instant entry; only the elder path detours via the font step
 *   step 3 — seniors only: accessibility font-size slider with live preview
 * Closing the app (or logging out) lands back here at the language step.
 */
public class OnboardingActivity extends AppCompatActivity {

    static final String PREFS = "kinbridge_prefs";
    static final String KEY_ROLE = "user_role";
    static final String KEY_STEP = "onboarding_step";
    static final String ROLE_SENIOR = "senior";
    static final String ROLE_GUARDIAN = "guardian";
    static final String ROLE_CARE = "care";

    /** Role-based home (used by SuccessActivity and role-select). */
    static Class<?> routeForRole(Context context) {
        String role = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROLE, ROLE_SENIOR);
        if (ROLE_GUARDIAN.equals(role)) {
            return DashboardActivity.class;
        }
        if (ROLE_CARE.equals(role)) {
            return CarePanelActivity.class;
        }
        return ElderHomeActivity.class;
    }

    static final String ROLE_EXTRA = "com.amigos.kinbridge.ROLE";

    /** Display name for a role in the current locale. */
    static String roleDisplayName(Context context, String role) {
        if (ROLE_GUARDIAN.equals(role)) {
            return context.getString(R.string.role_guardian_name);
        }
        if (ROLE_CARE.equals(role)) {
            return context.getString(R.string.role_care_name);
        }
        return context.getString(R.string.role_elder);
    }

    private static final long FADE_MS = 150;
    private static final long GREETING_SWAP_MS = 120;
    private static final long GREETING_PERIOD_MS = 1800;
    private static final float PREVIEW_BASE_SP = 18f;

    /** Greeting + companion prompt, alternating Indonesian and English. */
    private static final String[][] GREETINGS = {
            {"Selamat datang!", "Bahasa apa yang ingin Anda gunakan?"},
            {"Welcome!", "What language would you like to speak?"},
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

    private TextView fontPreview;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // A live session skips welcome + login and goes straight to the
        // account's role home. Without one, every cold start shows the
        // language greeting (and logging out always returns here).
        com.google.firebase.auth.FirebaseUser currentUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(this, routeForRole(this)));
            finish();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        fontPreview = findViewById(R.id.fontPreview);

        // Fresh entries (cold start, or right after logout) always begin at the
        // language step. Only the locale-switch recreate restores the in-flight
        // step (role), which is what pendingFadeIn marks.
        int step;
        if (pendingFadeIn) {
            step = prefs.getInt(KEY_STEP, 1);
        } else {
            step = 1;
            prefs.edit().putInt(KEY_STEP, 1).apply();
        }
        findViewById(R.id.stepLanguage).setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.stepRole).setVisibility(step == 2 ? View.VISIBLE : View.GONE);

        findViewById(R.id.optionIndonesian).setOnClickListener(v -> chooseLanguage("id"));
        findViewById(R.id.optionEnglish).setOnClickListener(v -> chooseLanguage("en"));

        // Role-select: instant entry per profile (MAIN_WORKFLOW stop flag).
        findViewById(R.id.optionSenior).setOnClickListener(v -> enterSenior());
        findViewById(R.id.optionGuardian).setOnClickListener(v -> enterRole(ROLE_GUARDIAN));
        findViewById(R.id.optionCare).setOnClickListener(v -> enterRole(ROLE_CARE));

        findViewById(R.id.backToLanguage).setOnClickListener(v -> backToLanguageStep());
        findViewById(R.id.authLink).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.backToRole).setOnClickListener(v ->
                crossfadeSteps(findViewById(R.id.stepFont), findViewById(R.id.stepRole)));
        findViewById(R.id.fontOkButton).setOnClickListener(v -> proceedToCompanion());

        SeekBar fontSlider = findViewById(R.id.fontSlider);
        fontSlider.setMax(FontScale.stepCount() - 1);
        fontSlider.setProgress(prefs.getInt(FontScale.KEY_FONT_STEP, 0));
        fontSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Raw pixels: the preview must show the NEW selection, unaffected
                // by the currently applied fontScale.
                float density = getResources().getDisplayMetrics().density;
                fontPreview.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        PREVIEW_BASE_SP * density * FontScale.scaleForStep(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (findViewById(R.id.stepFont).getVisibility() == View.VISIBLE) {
                    crossfadeSteps(findViewById(R.id.stepFont), findViewById(R.id.stepRole));
                } else if (findViewById(R.id.stepRole).getVisibility() == View.VISIBLE) {
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

    // ---- Step 2: role-select (instant entry) ----

    private void enterSenior() {
        if (transitioning) {
            return;
        }
        prefs.edit().putString(KEY_ROLE, ROLE_SENIOR).apply();
        // Elders get the accessibility font step before the companion.
        crossfadeSteps(findViewById(R.id.stepRole), findViewById(R.id.stepFont));
    }

    private void enterRole(String role) {
        if (transitioning) {
            return;
        }
        transitioning = true;
        prefs.edit().putString(KEY_ROLE, role).putInt(KEY_STEP, 1).apply();
        fadeOut(() -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(ROLE_EXTRA, role);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void backToLanguageStep() {
        prefs.edit().putInt(KEY_STEP, 1).apply();

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
        // Picking the ALREADY-active locale triggers no recreate — so the
        // fade-out would strand a dead white screen. Swap steps in place instead.
        String current = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        boolean same = ("id".equals(languageTag) && (current.startsWith("id") || current.startsWith("in")))
                || ("en".equals(languageTag) && current.startsWith("en"));
        if (same) {
            prefs.edit().putInt(KEY_STEP, 2).apply();
            crossfadeSteps(findViewById(R.id.stepLanguage), findViewById(R.id.stepRole));
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

    private void proceedToCompanion() {
        if (transitioning) {
            return;
        }
        transitioning = true;
        SeekBar fontSlider = findViewById(R.id.fontSlider);
        prefs.edit()
                .putInt(FontScale.KEY_FONT_STEP, fontSlider.getProgress())
                .putInt(KEY_STEP, 1)
                .apply();
        fadeOut(() -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(ROLE_EXTRA, ROLE_SENIOR);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void crossfadeSteps(View from, View to) {
        from.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            from.setVisibility(View.GONE);
            from.setAlpha(1f);
            to.setAlpha(0f);
            to.setVisibility(View.VISIBLE);
            to.animate().alpha(1f).setDuration(FADE_MS).start();
        }).start();
    }

    private void fadeOut(Runnable after) {
        findViewById(android.R.id.content).animate()
                .alpha(0f).setDuration(FADE_MS)
                .withEndAction(after).start();
    }
}
