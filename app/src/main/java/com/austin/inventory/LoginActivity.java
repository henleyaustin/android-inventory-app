package com.austin.inventory;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.austin.inventory.databinding.ActivityLoginBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    DatabaseHelper databaseHelper;
    ActivityLoginBinding binding;
    SharedPreferences preferences;
    private ExecutorService executorService;
    private Handler handler;
    private boolean registerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());

        preferences = getSharedPreferences("user_prefs", MODE_PRIVATE);

        databaseHelper = new DatabaseHelper(this);

        binding.registerButton.setOnClickListener(v -> toggleRegisterMode());

        binding.whyButton.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Why We Need Your Phone Number").setMessage("Your phone number is used for 2FA to login to the " + "app and for notifications, which can be activated in settings.").setPositiveButton("OK", ((dialog, which) -> dialog.dismiss())).show());

        binding.loginButton.setOnClickListener(view -> {
            String email = binding.editTextEmail.getText().toString();
            String password = binding.editTextPassword.getText().toString();
            String confirmPassword = binding.editConfirmPassword.getText().toString();
            String phoneNumber = binding.editPhoneNumber.getText().toString();

            if (!areFieldsValid(email, password, confirmPassword, phoneNumber)) {
                return;
            }

            if (registerMode) {
                handleRegistration(email, password, phoneNumber);
            } else {
                handleLogin(email, password);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private boolean areFieldsValid(String email, String password, String confirmPassword, String phoneNumber) {
        if (registerMode && (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || phoneNumber.isEmpty())) {
            showSnackbar("All fields are required");
            return false;
        } else if (!registerMode && (email.isEmpty() || password.isEmpty())) {
            showSnackbar("Email and password are required");
            return false;
        }

        if (registerMode) {
            if (!isValidEmail(email)) {
                showSnackbar("Invalid email format");
                return false;
            }
            if (!isValidPassword(password)) {
                showSnackbar("Password must contain a capital letter, a lowercase letter, a number, and a symbol");
                return false;
            }
            if (!password.equals(confirmPassword)) {
                showSnackbar("Passwords do not match");
                return false;
            }
        }

        return true;
    }

    private void handleRegistration(String email, String password, String phoneNumber) {
        if (!databaseHelper.checkUserEmail(email)) {
            if (databaseHelper.insertUser(email, password, phoneNumber)) {
                showSnackbar("Signup successful");
                resetToLoginMode();
            } else {
                showSnackbar("Signup failed");
            }
        } else {
            showSnackbar("User already exists - Please log in");
        }
    }


    private void handleLogin(String email, String password) {
        boolean isSmsEnabled = preferences.getBoolean("sms_notifications_enabled", false);

        executorService.execute(() -> {
            if (databaseHelper.checkUserEmail(email) && databaseHelper.checkUserCredentials(email, password)) {
                if (isSmsEnabled && databaseHelper.is2FAEnabled(email)) {
                    String phoneNumber = databaseHelper.getUserPhoneNumber(email);
                    String verificationCode = sendVerificationCode(phoneNumber);
                    handler.post(() -> promptForVerificationCode(verificationCode));
                } else {
                    handler.post(() -> {
                        saveLoggedInUser(email);
                        navigateToMainActivity();
                    });
                }
            } else {
                handler.post(() -> showSnackbar("Invalid email or password"));
            }
        });
    }


    private String sendVerificationCode(String phoneNumber) {

        String verificationCode = generateVerificationCode();
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, "Your verification code is: " + verificationCode, null, null);
            Log.d("LoginActivity", "Verification code sent: " + verificationCode);
        } catch (Exception e) {
            Log.e("LoginActivity", "SMS failed to send", e);
            showSnackbar("Failed to send verification code");
        }
        return verificationCode; // Return the generated code
    }


    private void promptForVerificationCode(String correctCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Verification Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String enteredCode = input.getText().toString();
            if (enteredCode.equals(correctCode)) {
                navigateToMainActivity();
            } else {
                showSnackbar("Incorrect verification code");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveLoggedInUser(String email) {

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("logged_in_user_email", email);
        editor.apply();
        Log.d("LoginActivity", "Saved user email: " + email); // Add this log
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void toggleRegisterMode() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.loginButton.getLayoutParams();

        if (!registerMode) {
            // Switch to register mode
            binding.editConfirmPassword.setVisibility(View.VISIBLE);
            binding.editPhoneNumber.setVisibility(View.VISIBLE); // Make phone number field visible
            binding.whyButton.setVisibility(View.VISIBLE);
            binding.loginButton.setText(getString(R.string.register));
            params.topToBottom = R.id.edit_phone_number; // Adjust login button position
            binding.registerButton.setText(R.string.cancel);
            binding.accountQuestion.setVisibility(View.GONE);
            registerMode = true;
        } else {
            // Switch to login mode
            binding.editConfirmPassword.setVisibility(View.GONE);
            binding.editPhoneNumber.setVisibility(View.GONE); // Hide phone number field
            binding.whyButton.setVisibility(View.GONE);
            binding.loginButton.setText(getString(R.string.login));
            params.topToBottom = R.id.edit_text_password; // Adjust login button position
            binding.registerButton.setText(getString(R.string.register));
            binding.accountQuestion.setVisibility(View.VISIBLE);
            registerMode = false;
        }
        binding.loginButton.setLayoutParams(params);
    }

    private void resetToLoginMode() {
        toggleRegisterMode(); // Switch back to login mode
        clearFields(); // Clear all input fields
    }

    private void clearFields() {
        binding.editTextEmail.setText("");
        binding.editTextPassword.setText("");
        binding.editConfirmPassword.setText("");
    }

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }

    private boolean isValidPassword(String password) {
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$";
        return password.matches(passwordRegex);
    }

    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // Generate 6 digit code
        return String.valueOf(code);
    }

}