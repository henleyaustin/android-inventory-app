/**
 * SettingsFragment.java
 *
 * This class is responsible for the the settings and preferences functionality
 *
 * Author: Austin Henley
 * Created on: 12/5/2023
 */

package com.austin.inventory;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.snackbar.Snackbar;

public class SettingsFragment extends PreferenceFragmentCompat {
    DatabaseHelper databaseHelper;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private SharedPreferences preferences;
    private String currentUserEmail;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        preferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        currentUserEmail = preferences.getString("logged_in_user_email", null);

        databaseHelper = new DatabaseHelper(getContext());

        SwitchPreferenceCompat smsPreference = findPreference("notifications");
        SwitchPreferenceCompat enable2FAPref = findPreference("enable_2fa");
        SwitchPreferenceCompat notifyInventoryZeroPref = findPreference("notify_inventory_zero");

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                updateSmsPreference(true);
            } else {
                showSnackbar("SMS permission denied");
                resetSmsPreference();
            }
        });

        if (smsPreference != null) {
            smsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue) {
                    showSmsExplanationDialog();
                } else {
                    resetSmsPreference();
                }
                return true;
            });
        }

        if (enable2FAPref != null && smsPreference != null) {
            enable2FAPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean is2FAEnabled = (Boolean) newValue;

                if (is2FAEnabled && !smsPreference.isChecked()) {
                    showSnackbar("2FA cannot be enabled as SMS notifications are disabled.");
                    return false;
                } else {
                    databaseHelper.updateUser2FASetting(currentUserEmail, is2FAEnabled);
                    return true;
                }
            });
        }

        EditTextPreference minimumInventoryPref = findPreference("minimum_inventory");
        if (minimumInventoryPref != null) {
            minimumInventoryPref.setOnPreferenceChangeListener((preference, newValue) -> {
                int minInventory = Integer.parseInt((String) newValue);
                preferences.edit().putInt("minimum_inventory_value", minInventory).apply();
                return true;
            });
        }

        if (notifyInventoryZeroPref != null && smsPreference != null) {
            notifyInventoryZeroPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean notifyWhenZero = (Boolean) newValue;
                if (notifyWhenZero && !smsPreference.isChecked()) {
                    showSnackbar("Inventory zero notifications require SMS to be enabled.");
                    return false;
                } else {
                    preferences.edit().putBoolean("notify_inventory_zero", notifyWhenZero).apply();
                    return true;
                }
            });
        }
    }

    /**
     * Show SMS preference explanation
     */
    private void showSmsExplanationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("SMS Notifications")
                .setMessage("Enabling SMS notifications will allow the app to send you alerts via text message, and use two-factor authentication at login." +
                        "The app will request permission to send SMS.")
                .setPositiveButton("OK", (dialog, which) -> checkForSmsPermission())
                .setNegativeButton("Cancel", (dialog, which) -> resetSmsPreference())
                .show();
    }

    /**
     * Updates the SMS preference
     *
     * @param enabled bool indicating SMS preference
     */
    private void updateSmsPreference(boolean enabled) {
        preferences.edit().putBoolean("sms_notifications_enabled", enabled).apply();
        String message = enabled ? "SMS notifications enabled" : "SMS notifications disabled";
        showSnackbar(message);
    }


    /**
     * Request SMS permission
     */
    private void checkForSmsPermission() {
        requestPermissionLauncher.launch(Manifest.permission.SEND_SMS);
    }

    /**
     * Resets the SMS notification preference in shared preferences -
     * Called when user sets SMS permission to no (If they previously gave the app permission)
     */
    private void resetSmsPreference() {
        updateSmsPreference(false);

        // Automatically turn off 2FA if SMS is turned off
        if (currentUserEmail != null) {
            databaseHelper.updateUser2FASetting(currentUserEmail, false);
            SwitchPreferenceCompat enable2FAPref = findPreference("enable_2fa");
            if (enable2FAPref != null) {
                enable2FAPref.setChecked(false);
                showSnackbar("2FA has been disabled as SMS notifications are turned off");
            }
        }

        SwitchPreferenceCompat smsPreference = findPreference("notifications");
        if (smsPreference != null) {
            smsPreference.setChecked(false);
        }
    }


    /**
     * Show snackbar notification in app
     *
     * @param message message to be sent to user
     */
    private void showSnackbar(String message) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show();
    }
}
