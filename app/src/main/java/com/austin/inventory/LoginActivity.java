/**
 * LoginActivity.java
 *
 * This class is responsible for the the Login activity containing all logging in and registering functionality for the app
 *
 * Author: Austin Henley
 * Created on: 12/3/2023
 *
 * Utilizes executor service for Asynchronous tasks
 * Documentation: https://developer.android.com/reference/java/util/concurrent/ExecutorService
 *
 * Utilizes smsManager for sending SMS - Update to Twilio in future
 * Documentation: https://developer.android.com/reference/android/telephony/SmsManager
 *
 * Utilizes Regex for validating email and password formatting
 * Documentation used: https://www.abstractapi.com/tools/email-regex-guide
 */

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

        // Switch to register mode
        binding.registerButton.setOnClickListener(v -> toggleRegisterMode());

        // Create dialog for why button
        binding.whyButton.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Why We Need Your Phone Number")
                .setMessage("Your phone number is used for 2FA to login to the " + "app and for notifications, which can be activated in settings.")
                .setPositiveButton("OK", ((dialog, which) -> dialog.dismiss())).show());

        // Actions for login button - Captures all fields
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

    /**
     * Checks each field to ensure valid data
     * @param email user entered email
     * @param password user entered password
     * @param confirmPassword user entered password from confirm password field
     * @param phoneNumber user entered phone number
     * @return
     */
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

    /**
     * Insert user into table
     * @param email email of user
     * @param password password of user
     * @param phoneNumber phone number of user
     */
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

    /**
     * Method handling login functionality
     * @param email email of user
     * @param password password of user
     */
    private void handleLogin(String email, String password) {
        // Check if SMS is enabled (For 2FA)
        boolean isSmsEnabled = preferences.getBoolean("sms_notifications_enabled", false);

        // Execute database operations asynchronously
        executorService.execute(() -> {
            if (databaseHelper.checkUserEmail(email) && databaseHelper.checkUserCredentials(email, password)) {
                if (isSmsEnabled && databaseHelper.is2FAEnabled(email)) {
                    String phoneNumber = databaseHelper.getUserPhoneNumber(email);
                    String verificationCode = sendVerificationCode(phoneNumber);
                    handler.post(() -> promptForVerificationCode(verificationCode, email));
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


    /**
     * Send code to user for 2FA
     * @param phoneNumber phone number to send 2FA to
     * @return generated 2FA code
     */
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

    /**
     * Build dialog for 2FA window
     * @param correctCode 2FA code being checked against
     */
    private void promptForVerificationCode(String correctCode, String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Verification Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String enteredCode = input.getText().toString();
            if (enteredCode.equals(correctCode)) {
                saveLoggedInUser(email);
                navigateToMainActivity();
            } else {
                showSnackbar("Incorrect verification code");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }


    /**
     * Save user to local shared preferences
     * @param email email of user
     */
    private void saveLoggedInUser(String email) {

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("logged_in_user_email", email);
        editor.apply();
        Log.d("LoginActivity", "Saved user email: " + email); // Add this log
    }

    /**
     * Navigate app to main activity and shut down login activity
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Switch login page to register information
     */
    private void toggleRegisterMode() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.loginButton.getLayoutParams();

        if (!registerMode) {
            // Switch to register mode
            binding.editConfirmPassword.setVisibility(View.VISIBLE); // Make edit password field visible
            binding.editPhoneNumber.setVisibility(View.VISIBLE); // Make phone number field visible
            binding.whyButton.setVisibility(View.VISIBLE); // Make "why?" button visible
            binding.loginButton.setText(getString(R.string.register)); // Change "login" button to "Register"
            params.topToBottom = R.id.edit_phone_number; // Adjust login button position
            binding.registerButton.setText(R.string.cancel); // Set register button to "cancel"
            binding.accountQuestion.setVisibility(View.GONE); // Hide "new user" text
            registerMode = true;
        } else {
            // Switch to login mode
            binding.editConfirmPassword.setVisibility(View.GONE);// Hide edit password field
            binding.editPhoneNumber.setVisibility(View.GONE); // Hide phone number field
            binding.whyButton.setVisibility(View.GONE); // Hide why button
            binding.loginButton.setText(getString(R.string.login)); // Set "Login" button to "Login"
            params.topToBottom = R.id.edit_text_password; // Adjust login button position
            binding.registerButton.setText(getString(R.string.register)); // Set register button to "Register"
            binding.accountQuestion.setVisibility(View.VISIBLE); // Show "new user" text
            registerMode = false;
        }
        binding.loginButton.setLayoutParams(params);
    }

    /**
     * Quick method for switching back to login mode
     */
    private void resetToLoginMode() {
        toggleRegisterMode();
        clearFields();
    }

    /**
     * Clear all input fields
     */
    private void clearFields() {
        binding.editTextEmail.setText("");
        binding.editTextPassword.setText("");
        binding.editConfirmPassword.setText("");
        binding.editPhoneNumber.setText("");
    }

    /**
     * Show snackbar notification in app
     * @param message message to be sent to user
     */
    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * Use regex to verify email format
     * @param email email to check against
     * @return "true" if email is valid, "false" if not
     */
    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return email.matches(emailRegex);
    }

    /**
     * Use regex to verify password format
     * @param password password to check against
     * @return "true" if password is valid, "false" if not
     */
    private boolean isValidPassword(String password) {
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$";
        return password.matches(passwordRegex);
    }

    /**
     * Generate random verification code for 2FA
     * @return string verification code
     */
    private String generateVerificationCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

}