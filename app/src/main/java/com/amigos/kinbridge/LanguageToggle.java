package com.amigos.kinbridge;

import android.app.Activity;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Binds the flag language toggle in the auth screens' top bar.
 * Highlights the active locale; a tap applies a per-app locale, which AppCompat
 * persists and re-applies on every launch (the activity then recreates itself
 * with the new configuration).
 */
final class LanguageToggle {

    private LanguageToggle() {
    }

    static void bind(Activity activity) {
        ImageView langId = activity.findViewById(R.id.langId);
        ImageView langEn = activity.findViewById(R.id.langEn);
        if (langId == null || langEn == null) {
            return;
        }

        boolean indonesianActive = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags().startsWith("in");
        langId.setBackgroundResource(indonesianActive ? R.drawable.bg_lang_selected : 0);
        langEn.setBackgroundResource(indonesianActive ? 0 : R.drawable.bg_lang_selected);

        langId.setOnClickListener(v -> setLocale("in"));
        langEn.setOnClickListener(v -> setLocale("en"));
    }

    private static void setLocale(String languageTag) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }
}
