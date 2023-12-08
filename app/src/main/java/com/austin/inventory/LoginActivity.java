package com.austin.inventory;

import static android.app.PendingIntent.getActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.austin.inventory.databinding.ActivityLoginBinding;
import com.google.android.material.snackbar.Snackbar;

public class LoginActivity extends AppCompatActivity {

    DatabaseHelper databaseHelper;
    ActivityLoginBinding binding;
    private boolean registerMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        databaseHelper = new DatabaseHelper(this);

        binding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRegisterMode();
            }
        });


        binding.loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String email = binding.editTextEmail.getText().toString();
                String password = binding.editTextPassword.getText().toString();
                String confirmPassword = binding.editConfirmPassword.getText().toString();
                String phoneNumber = binding.editPhoneNumber.getText().toString();

                if (registerMode) {
                    if (email.equals("") || password.equals("") || confirmPassword.equals("") || phoneNumber.equals("")) {
                        showSnackbar("All fields are required");
                    } else {
                        if (password.equals(confirmPassword)){
                            Boolean checkEmail = databaseHelper.checkUserEmail(email);

                            if (checkEmail == false) {
                                Boolean insert = databaseHelper.insertUser(email, password, phoneNumber);

                                if (insert == true) {
                                    showSnackbar("Signup successful");
                                    resetToLoginMode(); // Reset to login mode after successful registration
                                } else {
                                    showSnackbar("Signup failed");
                                }
                            } else {
                                showSnackbar("User already exists - Please log in");
                            }
                        } else {
                            showSnackbar("Passwords do not match");
                        }
                    }
                } else {
                    if (email.equals("") || password.equals("")) {
                        showSnackbar("All fields are required");
                    } else {
                        Boolean checkEmail = databaseHelper.checkUserEmail(email);

                        if (checkEmail) {
                            Boolean checkCredentials = databaseHelper.checkUserCredentials(email, password);

                            if (checkCredentials == true) {
                                showSnackbar("Login successful");
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                showSnackbar("Password incorrect");
                            }
                        } else {
                            showSnackbar("This login does not exist - Please register");
                        }

                    }
                }
            }
        });
    }
    private void toggleRegisterMode() {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.loginButton.getLayoutParams();

        if (registerMode) {
            // Show Confirm Password and Phone Number fields in register mode
            binding.editConfirmPassword.setVisibility(View.VISIBLE);
            binding.editPhoneNumber.setVisibility(View.VISIBLE); // Make phone number field visible
            binding.loginButton.setText("Register");
            params.topToBottom = R.id.edit_phone_number; // Adjust login button position
            binding.registerButton.setText("Cancel");
            binding.accountQuestion.setVisibility(View.GONE);
            registerMode = false;
        } else {
            // Hide Confirm Password and Phone Number fields in login mode
            binding.editConfirmPassword.setVisibility(View.GONE);
            binding.editPhoneNumber.setVisibility(View.GONE); // Hide phone number field
            binding.loginButton.setText("Login");
            params.topToBottom = R.id.edit_text_password; // Adjust login button position
            binding.registerButton.setText("Register");
            binding.accountQuestion.setVisibility(View.VISIBLE);
            registerMode = true;
        }
        binding.loginButton.setLayoutParams(params);
        clearFields();
    }

    private void resetToLoginMode() {
        toggleRegisterMode(); // Switch back to login mode
        clearFields(); // Clear all input fields
    };

    private void clearFields() {
        binding.editTextEmail.setText("");
        binding.editTextPassword.setText("");
        binding.editConfirmPassword.setText("");
    };

    private void showSnackbar(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
    }

}