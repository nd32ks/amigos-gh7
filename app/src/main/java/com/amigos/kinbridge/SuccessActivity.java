package com.amigos.kinbridge;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SuccessActivity extends AppCompatActivity {

    static final String EXTRA_EMAIL = "com.amigos.kinbridge.EXTRA_EMAIL";
    static final String EXTRA_TITLE = "com.amigos.kinbridge.EXTRA_TITLE";

    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_success);

        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        TextView headline = findViewById(R.id.successHeadline);
        headline.setText(title != null ? title : getString(R.string.login_successful));

        TextView subtitle = findViewById(R.id.successSubtitle);
        subtitle.setText(getString(R.string.signed_in_as, email == null ? "" : email));

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            userRepository.signOut();
            Intent intent = new Intent(this, OnboardingActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
