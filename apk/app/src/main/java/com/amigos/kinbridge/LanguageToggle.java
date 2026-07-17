package com.amigos.kinbridge;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Binds the flag language toggle in the auth screens' top bar.
 *
 * Switching fades the content out, applies the per-app locale (AppCompat
 * persists it and recreates the activity), and fades the new instance back in —
 * no black flash, no choppy default transition. Tapping the already-active
 * flag is a no-op so it never flickers pointlessly.
 */
final class LanguageToggle {

    private static final long FADE_MS = 150;

    /** Set before a locale switch so the recreated instance knows to fade in. */
    private static boolean pendingFadeIn;

    private LanguageToggle() {
    }

    static void bind(Activity activity) {
        ImageView langId = activity.findViewById(R.id.langId);
        ImageView langEn = activity.findViewById(R.id.langEn);
        if (langId == null || langEn == null) {
            return;
        }

        paintSelection(langId, langEn, isIndonesianActive());

        langId.setOnClickListener(v -> switchLanguage(activity, true));
        langEn.setOnClickListener(v -> switchLanguage(activity, false));

        if (pendingFadeIn) {
            pendingFadeIn = false;
            View content = activity.findViewById(android.R.id.content);
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(FADE_MS).start();
        }
    }

    private static void switchLanguage(Activity activity, boolean toIndonesian) {
        if (pendingFadeIn || toIndonesian == isIndonesianActive()) {
            return;
        }

        // Instant feedback, before the transition starts.
        paintSelection(activity.findViewById(R.id.langId),
                activity.findViewById(R.id.langEn), toIndonesian);

        pendingFadeIn = true;
        View content = activity.findViewById(android.R.id.content);
        content.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(toIndonesian ? "id" : "en"));
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }).start();
    }

    private static boolean isIndonesianActive() {
        // Java normalizes the legacy Indonesian tag "in" to "id" — accept both.
        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        return tags.startsWith("id") || tags.startsWith("in");
    }

    private static void paintSelection(ImageView langId, ImageView langEn, boolean indonesianActive) {
        langId.setBackgroundResource(indonesianActive ? R.drawable.bg_lang_selected : 0);
        langEn.setBackgroundResource(indonesianActive ? 0 : R.drawable.bg_lang_selected);
    }
}
