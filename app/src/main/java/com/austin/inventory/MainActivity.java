/**
 * SettingsFragment.java
 *
 * This class is responsible for the main activity of the app. Main activity acts as container for
 * inventory fragment and settings fragment
 *
 * Author: Austin Henley
 * Created on: 11/27/2023
 *
 * Uses toolbar for navbar functionality
 * Documentation used: https://developer.android.com/reference/android/widget/Toolbar
 */

package com.austin.inventory;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up toolbar
        Toolbar myToolbar = findViewById(R.id.inventory_toolbar);
        setSupportActionBar(myToolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupActionBarWithNavController(this, navController);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.appbar_menu, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController != null && navController.navigateUp() || super.onSupportNavigateUp();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            if (!(Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.settings)) {
                navigateToSettingsFragment();
            }
            return true;
        }

        if (id == R.id.action_logout) {
            clearLoggedInUser();

            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Navigate to settings fragment from inventory fragment
     */
    private void navigateToSettingsFragment() {
        if (navController != null) {
            navController.navigate(R.id.action_inventory_to_settings);
        }
    }

    /**
     * Clear current user email from preferences
     */
    private void clearLoggedInUser() {
        SharedPreferences preferences = getSharedPreferences("user_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("logged_in_user_email");
        editor.apply();
    }

}