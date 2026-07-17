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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * First-run flow, shown whenever there is no signed-in user:
 *   step 1 — language, with an Indonesian/English rotating greeting
 *   step 2 — role, select a card then Continue (Back returns to step 1)
 *   step 3 — seniors only: accessibility font-size slider with live preview
 * Closing the app without logging in (or logging out) lands back here.
 */
public class OnboardingActivity extends AppCompatActivity {

    static final String PREFS = "kinbridge_prefs";
    static final String KEY_ROLE = "user_role";
    static final String KEY_STEP = "onboarding_step";
    static final String ROLE_SENIOR = "senior";
    static final String ROLE_VOLUNTEER = "volunteer";

    /** Role-based home: seniors get the companion, volunteers the dashboard. */
    static Class<?> routeForRole(Context context) {
        String role = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ROLE, ROLE_SENIOR);
        return ROLE_VOLUNTEER.equals(role) ? DashboardActivity.class : CompanionActivity.class;
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

    private String selectedRole;
    private boolean roleChosen;
    private View optionSenior;
    private View optionVolunteer;
    private Button continueButton;
    private TextView fontPreview;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // A live session skips the welcome flow entirely and goes straight to
        // the role's home; without one, every cold start begins at language.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(this, routeForRole(this)));
            finish();
            return;
        }

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(R.layout.activity_onboarding);

        optionSenior = findViewById(R.id.optionSenior);
        optionVolunteer = findViewById(R.id.optionVolunteer);
        continueButton = findViewById(R.id.continueButton);
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
        optionSenior.setOnClickListener(v -> selectRole(ROLE_SENIOR));
        optionVolunteer.setOnClickListener(v -> selectRole(ROLE_VOLUNTEER));
        continueButton.setOnClickListener(v -> continueFromRole());
        findViewById(R.id.backToLanguage).setOnClickListener(v -> backToLanguageStep());
        findViewById(R.id.backToRole).setOnClickListener(v ->
                crossfadeSteps(findViewById(R.id.stepFont), findViewById(R.id.stepRole)));
        findViewById(R.id.fontOkButton).setOnClickListener(v -> proceedToLogin());

        SeekBar fontSlider = findViewById(R.id.fontSlider);
        fontSlider.setMax(FontScale.stepCount() - 1);
        fontSlider.setProgress(prefs.getInt(FontScale.KEY_FONT_STEP, 0));
        fontSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Render the preview in raw pixels so it is not affected by the
                // currently applied fontScale — it must show the NEW selection.
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

    // ---- Step 2: role selection ----

    private void selectRole(String role) {
        if (transitioning) {
            return;
        }
        selectedRole = role;
        optionSenior.setSelected(ROLE_SENIOR.equals(role));
        optionVolunteer.setSelected(ROLE_VOLUNTEER.equals(role));
        if (!roleChosen) {
            // Do NOT gate on isClickable() here — setOnClickListener() silently
            // makes the button clickable, which kept this fade from ever running.
            roleChosen = true;
            continueButton.animate().alpha(1f).setDuration(FADE_MS).start();
        }
    }

    private void continueFromRole() {
        if (transitioning || selectedRole == null) {
            return;
        }
        prefs.edit().putString(KEY_ROLE, selectedRole).apply();
        if (ROLE_SENIOR.equals(selectedRole)) {
            // Seniors get the accessibility font step before login.
            crossfadeSteps(findViewById(R.id.stepRole), findViewById(R.id.stepFont));
        } else {
            proceedToLogin();
        }
    }

    private void backToLanguageStep() {
        prefs.edit().putInt(KEY_STEP, 1).apply();

        selectedRole = null;
        roleChosen = false;
        optionSenior.setSelected(false);
        optionVolunteer.setSelected(false);
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

    private void proceedToLogin() {
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
            startActivity(new Intent(this, LoginActivity.class));
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
