package com.amigos.kinbridge;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * First-run onboarding, shown once before login:
 *   step 1 — language (Bahasa Indonesia / English)
 *   step 2 — role (senior citizen / social worker-volunteer)
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

    /** Set before the locale-switch recreate so the new instance fades back in. */
    private static boolean pendingFadeIn;

    private SharedPreferences prefs;
    private boolean transitioning;

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

        int step = prefs.getInt(KEY_STEP, 1);
        findViewById(R.id.stepLanguage).setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        findViewById(R.id.stepRole).setVisibility(step == 2 ? View.VISIBLE : View.GONE);

        findViewById(R.id.optionIndonesian).setOnClickListener(v -> chooseLanguage("id"));
        findViewById(R.id.optionEnglish).setOnClickListener(v -> chooseLanguage("en"));
        findViewById(R.id.optionSenior).setOnClickListener(v -> chooseRole(ROLE_SENIOR));
        findViewById(R.id.optionVolunteer).setOnClickListener(v -> chooseRole(ROLE_VOLUNTEER));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                View stepRole = findViewById(R.id.stepRole);
                if (stepRole.getVisibility() == View.VISIBLE) {
                    // Back to the language step with a gentle fade-in.
                    prefs.edit().putInt(KEY_STEP, 1).apply();
                    stepRole.setVisibility(View.GONE);
                    View stepLanguage = findViewById(R.id.stepLanguage);
                    stepLanguage.setAlpha(0f);
                    stepLanguage.setVisibility(View.VISIBLE);
                    stepLanguage.animate().alpha(1f).setDuration(FADE_MS).start();
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

    private void chooseRole(String role) {
        if (transitioning) {
            return;
        }
        transitioning = true;
        prefs.edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_ROLE, role)
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
