package com.amigos.kinbridge;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Floating right-edge tab → slide-in navigation drawer with every feature.
 * Bind on any main surface; the tab is added to the content view
 * programmatically, so no layout changes are needed per screen.
 */
final class SideMenu {

    static final String EXTRA_OPEN_DIARY = "com.amigos.kinbridge.OPEN_DIARY";

    private SideMenu() {
    }

    static void bind(AppCompatActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        float density = activity.getResources().getDisplayMetrics().density;

        TextView tab = new TextView(activity);
        tab.setText("☰");
        tab.setTextSize(16);
        tab.setTextColor(activity.getColor(R.color.signal_violet));
        tab.setGravity(Gravity.CENTER);
        tab.setBackgroundResource(R.drawable.bg_side_tab);
        tab.setContentDescription("Menu");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (30 * density), (int) (96 * density),
                Gravity.END | Gravity.CENTER_VERTICAL);
        tab.setLayoutParams(lp);
        tab.setOnClickListener(v -> showMenu(activity));
        content.addView(tab);
    }

    private static void showMenu(AppCompatActivity activity) {
        float density = activity.getResources().getDisplayMetrics().density;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_side_menu);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setGravity(Gravity.END);
            window.setLayout((int) (280 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            window.getAttributes().windowAnimations = R.style.SideMenuAnimations;
        }

        dialog.findViewById(R.id.menuCompanion).setOnClickListener(v ->
                go(activity, dialog, CompanionActivity.class, false));
        dialog.findViewById(R.id.menuDashboard).setOnClickListener(v ->
                go(activity, dialog, DashboardActivity.class, false));
        dialog.findViewById(R.id.menuCare).setOnClickListener(v ->
                go(activity, dialog, CarePanelActivity.class, false));
        dialog.findViewById(R.id.menuDiary).setOnClickListener(v ->
                go(activity, dialog, DashboardActivity.class, true));
        dialog.findViewById(R.id.menuWizard).setOnClickListener(v ->
                go(activity, dialog, OnboardWizardActivity.class, false));
        dialog.findViewById(R.id.menuRole).setOnClickListener(v ->
                go(activity, dialog, OnboardingActivity.class, false));
        dialog.findViewById(R.id.menuLogin).setOnClickListener(v ->
                go(activity, dialog, LoginActivity.class, false));

        dialog.show();
    }

    private static void go(AppCompatActivity activity, Dialog dialog,
                           Class<?> target, boolean openDiary) {
        dialog.dismiss();
        Intent intent = new Intent(activity, target);
        if (openDiary) {
            intent.putExtra(EXTRA_OPEN_DIARY, true);
        }
        activity.startActivity(intent);
        if (!activity.getClass().equals(target) && target != OnboardWizardActivity.class
                && target != LoginActivity.class) {
            // Main-surface hops replace the current screen; utilities stack on top.
            activity.finish();
        }
    }
}
