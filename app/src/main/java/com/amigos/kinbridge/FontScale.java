package com.amigos.kinbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * Accessibility font scaling. The chosen step persists in SharedPreferences and
 * every activity wraps its base context with the matching fontScale, so the
 * whole app renders at the size the user picked during onboarding.
 */
final class FontScale {

    static final String KEY_FONT_STEP = "font_step";

    private static final float[] STOPS = {1.0f, 1.125f, 1.25f, 1.375f, 1.5f};

    private FontScale() {
    }

    static int stepCount() {
        return STOPS.length;
    }

    static float scaleForStep(int step) {
        int clamped = Math.max(0, Math.min(step, STOPS.length - 1));
        return STOPS[clamped];
    }

    static Context wrap(Context base) {
        SharedPreferences prefs = base.getSharedPreferences(
                OnboardingActivity.PREFS, Context.MODE_PRIVATE);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = scaleForStep(prefs.getInt(KEY_FONT_STEP, 0));
        return base.createConfigurationContext(config);
    }
}
