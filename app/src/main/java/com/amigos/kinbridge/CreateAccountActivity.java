package com.amigos.kinbridge;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CreateAccountActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(FontScale.wrap(newBase));
    }

    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private TextView nameError;
    private TextView emailError;
    private TextView passwordError;
    private TextView confirmPasswordError;
    private TextView genderError;
    private TextView genderFemale;
    private TextView genderMale;
    private String selectedGender;
    private Button createAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        nameError = findViewById(R.id.nameError);
        emailError = findViewById(R.id.emailError);
        passwordError = findViewById(R.id.passwordError);
        confirmPasswordError = findViewById(R.id.confirmPasswordError);
        genderError = findViewById(R.id.genderError);
        genderFemale = findViewById(R.id.genderFemale);
        genderMale = findViewById(R.id.genderMale);

        genderFemale.setOnClickListener(v -> selectGender("female"));
        genderMale.setOnClickListener(v -> selectGender("male"));

        createAccountButton = findViewById(R.id.createAccountButton);
        createAccountButton.setOnClickListener(v -> attemptCreateAccount());

        TextView signInLink = findViewById(R.id.signInLink);
        signInLink.setOnClickListener(v -> finish());
    }

    private void selectGender(String gender) {
        selectedGender = gender;
        genderFemale.setSelected("female".equals(gender));
        genderMale.setSelected("male".equals(gender));
        genderError.setVisibility(View.GONE);
    }

    private void attemptCreateAccount() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        boolean nameValid = !name.isEmpty();
        boolean emailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean passwordValid = password.length() >= 8;
        boolean confirmValid = confirmPassword.equals(password) && passwordValid;
        boolean genderValid = selectedGender != null;

        setFieldError(nameInput, nameError, !nameValid);
        setFieldError(emailInput, emailError, !emailValid);
        setFieldError(passwordInput, passwordError, !passwordValid);
        setFieldError(confirmPasswordInput, confirmPasswordError, !confirmValid);
        genderError.setVisibility(genderValid ? View.GONE : View.VISIBLE);

        if (!nameValid || !emailValid || !passwordValid || !confirmValid || !genderValid) {
            return;
        }

        setLoading(true);
        userRepository.createAccount(name, selectedGender, email, password, new UserRepository.Callback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                Intent intent = new Intent(CreateAccountActivity.this, SuccessActivity.class);
                intent.putExtra(SuccessActivity.EXTRA_EMAIL, email);
                intent.putExtra(SuccessActivity.EXTRA_TITLE, getString(R.string.account_created));
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(CreateAccountActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        createAccountButton.setEnabled(!loading);
        createAccountButton.setText(loading ? R.string.creating_account : R.string.create_account);
    }

    private void setFieldError(EditText field, TextView error, boolean hasError) {
        error.setVisibility(hasError ? View.VISIBLE : View.GONE);
        field.setBackgroundResource(hasError ? R.drawable.bg_input_error : R.drawable.bg_input);
    }
}
