package com.austin.inventory;

import static androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme;
import static androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends PreferenceFragmentCompat {
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (!phoneNumberExists()) {
                            promptForPhoneNumber();
                        } else {
                            updateSmsPreference(true);
                        }
                    } else {
                        showSnackbar("SMS permission denied");
                        resetSmsPreference();
                    }
                });

        SwitchPreferenceCompat smsPreference = findPreference("notifications");
        if (smsPreference != null) {
            smsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue) {
                    checkForSmsPermission();
                } else {
                    resetSmsPreference();
                }
                return true;
            });
        }
    }

    /**
     * Checks preferences to see whether the phone number exists
     * @return boolean of whether the phone number already exists
     */
    private boolean phoneNumberExists() {
        String phoneNumber = sharedPreferences.getString("user_phone_number", null);
        return phoneNumber != null && !phoneNumber.isEmpty();
    }

    /**
     * Updates the SMS prefence
     * @param enabled bool indicating SMS preference
     */
    private void updateSmsPreference(boolean enabled) {
        sharedPreferences.edit().putBoolean("sms_notifications_enabled", enabled).apply();
    }

    /**
     * Request SMS permission
     */
    private void checkForSmsPermission() {
        requestPermissionLauncher.launch(Manifest.permission.SEND_SMS);
    }

    /**
     * Prompt user for phone number using an alert dialog
     */
    private void promptForPhoneNumber() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Enter Phone Number");

        final EditText input = new EditText(getContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String phoneNumber = input.getText().toString();
            savePhoneNumber(phoneNumber);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * Method used for saving phone number utilizing encrypted preferences -
     * Implementation found in documentation:
     * https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
     *
     * @param phoneNumber phone number to be saved
     */
    private void savePhoneNumber(String phoneNumber) {
        try {
            MasterKey masterKey = new MasterKey.Builder(getContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences encryptedSharedPreferences = EncryptedSharedPreferences.create(
                    getContext(),
                    "encrypted_preferences",
                    masterKey,
                    PrefKeyEncryptionScheme.AES256_SIV,
                    PrefValueEncryptionScheme.AES256_GCM
            );

            encryptedSharedPreferences.edit().putBoolean("sms_notifications_enabled", true)
                    .putString("user_phone_number", phoneNumber).apply();
        } catch (Exception e) {
            e.printStackTrace();
            showSnackbar("Error saving phone number");
        }
    }

    /**
     * Resets the SMS notification preference in shared preferences -
     * Called when user sets sms permission to no (If they previously gave the app permission)
     */
    private void resetSmsPreference() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        preferences.edit().putBoolean("sms_notifications_enabled", false).apply();

        SwitchPreferenceCompat smsPreference = findPreference("notifications");
        if (smsPreference != null) {
            smsPreference.setChecked(false);
        }
    }

    /**
     * Show snackbar notification in app
     * @param message message to be sent to user
     */
    private void showSnackbar(String message) {
        Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
    }
}
