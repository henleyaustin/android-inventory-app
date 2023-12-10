/**
 * DatabaseHelper.java
 *
 * This class is responsible for all database operations within the application.
 * It handles the creation and upgrading of the database and provides methods for CRUD operations on user
 * and inventory data.
 *
 * Author: Austin Henley
 * Created on: 12/6/2023
 *
 * Note: This class uses SQLite for database operations and interacts with the InventoryItem model
 */

package com.austin.inventory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "InventoryApp.db";
    private static final int DATABASE_VERSION = 5;

    // Users table
    private static final String TABLE_USERS = "allusers";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_PASSWORD = "password";
    private static final String COLUMN_PHONE = "phone";
    private static final String COLUMN_2FA_ENABLED = "two_fa_enabled";

    // Inventory table
    private static final String TABLE_INVENTORY = "inventory";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_QUANTITY = "quantity";
    private static final String COLUMN_USER_EMAIL = "user_email";

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Users Table
        db.execSQL("CREATE TABLE " + TABLE_USERS + "(" + COLUMN_EMAIL + " TEXT PRIMARY KEY, " + COLUMN_PASSWORD + " TEXT, " + COLUMN_PHONE + " TEXT, " + COLUMN_2FA_ENABLED + " INTEGER DEFAULT 0)");

        // Create inventory table
        db.execSQL("CREATE TABLE " + TABLE_INVENTORY + "(" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_NAME + " TEXT, " + COLUMN_QUANTITY + " INTEGER, " + COLUMN_USER_EMAIL + " TEXT, " + "FOREIGN KEY(" + COLUMN_USER_EMAIL + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_EMAIL + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY);

        onCreate(db);
    }

    //region User Operations

    /**
     * Insert user into table
     * @param email email of user being inserted
     * @param password password of user being inserted
     * @param phone phone number of user being inserted
     * @return "true" if successful, "false" if failed
     */
    public boolean insertUser(String email, String password, String phone) {
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            Log.e("DatabaseHelper", "Failed to hash password for user " + email);
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_EMAIL, email);
        contentValues.put(COLUMN_PASSWORD, hashedPassword);
        contentValues.put(COLUMN_PHONE, phone);
        long result = db.insert(TABLE_USERS, null, contentValues);
        db.close();
        return result != -1;
    }

    public boolean checkUserEmail(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_USERS + " WHERE " + COLUMN_EMAIL + " = ?", new String[]{email});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }

    /**
     * Check user credentials for logging in
     * @param email user email
     * @param password user password
     * @return "true" if successful, "false" if failed
     */
    public boolean checkUserCredentials(String email, String password) {
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            Log.e("DatabaseHelper", "Failed to hash password for login attempt");
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_USERS + " WHERE " + COLUMN_EMAIL + " = ? AND " + COLUMN_PASSWORD + " = ?", new String[]{email, hashedPassword});
        boolean valid = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return valid;
    }

    /**
     * Retrieve user phone number from table
     * @param email email of user being retrieved
     * @return Phone number as string
     */
    public String getUserPhoneNumber(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String phoneNumber = null;
        Cursor cursor = db.query(TABLE_USERS, new String[]{COLUMN_PHONE}, COLUMN_EMAIL + " = ?", new String[]{email}, null, null, null);

        if (cursor.moveToFirst()) {
            int phoneIndex = cursor.getColumnIndex(COLUMN_PHONE);
            if (phoneIndex != -1) {
                phoneNumber = cursor.getString(phoneIndex);
            }
        }

        cursor.close();
        db.close();
        return phoneNumber;
    }

    /**
     * Update 2FA settings in users table
     * @param email email of user being updated
     * @param is2FAEnabled boolean 2FA is being updated to
     */
    public void updateUser2FASetting(String email, boolean is2FAEnabled) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_2FA_ENABLED, is2FAEnabled ? 1 : 0); // Convert boolean to integer
        int numRowsUpdated = db.update(TABLE_USERS, contentValues, COLUMN_EMAIL + " = ?", new String[]{email});
        Log.d("DatabaseHelper", "Number of rows updated: " + numRowsUpdated); // Log to check if the update is successful
        db.close();
    }


    /**
     * Check to see if user has 2FA enabled
     * @param email email of logged in user
     * @return "true" if user has 2FA enabled, "false" if they do not
     */
    public boolean is2FAEnabled(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, new String[]{COLUMN_2FA_ENABLED}, COLUMN_EMAIL + " = ?", new String[]{email}, null, null, null);
        boolean isEnabled = false;
        if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COLUMN_2FA_ENABLED);
            if (columnIndex != -1) {
                isEnabled = cursor.getInt(columnIndex) == 1;
            }
        }

        cursor.close();
        db.close();
        return isEnabled;
    }

    /**
     * Simple hashing of password using SHA-256
     * <a href="https://mkyong.com/java/java-sha-hashing-example/">Examples</a>
     * @param password password being hashed
     * @return Hashed password
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("DatabaseHelper", "NoSuchAlgorithmException in hashPassword", e);
            return null;
        }
    }
    //endregion

    //region Inventory Operations
    /**
     * Add inventory item to table
     * @param name name of item
     * @param quantity quantity of item
     * @param userEmail email of user item belongs to
     * @return "true" if successful, "false" if failed
     */
    public boolean insertInventoryItem(String name, int quantity, String userEmail) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_NAME, name);
        contentValues.put(COLUMN_QUANTITY, quantity);
        contentValues.put(COLUMN_USER_EMAIL, userEmail); // Add user email to ContentValues
        long result = db.insert(TABLE_INVENTORY, null, contentValues);
        db.close();
        return result != -1;
    }

    /**
     * Use foreign key to find all inventory items for specific user
     * @param userEmail email of user
     * @return list of inventory items
     */
    public List<InventoryItem> getInventoryItemsForUser(String userEmail) {
        List<InventoryItem> itemList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_INVENTORY + " WHERE " + COLUMN_USER_EMAIL + " = ?", new String[]{userEmail});

        // Getting column indexes
        int idIndex = cursor.getColumnIndex(COLUMN_ID);
        int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
        int quantityIndex = cursor.getColumnIndex(COLUMN_QUANTITY);

        if (idIndex != -1 && nameIndex != -1 && quantityIndex != -1) {
            if (cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(idIndex);
                    String name = cursor.getString(nameIndex);
                    int quantity = cursor.getInt(quantityIndex);
                    InventoryItem item = new InventoryItem(id, name, quantity);
                    itemList.add(item);
                } while (cursor.moveToNext());
            }
        }

        cursor.close();
        db.close();
        return itemList;
    }

    /**
     * Update inventory item details
     * @param id id of item being updated
     * @param name updated name of item
     * @param quantity updated quantity of item
     * @return "true" if successful, "false" if failed
     */
    public boolean updateInventoryItem(int id, String name, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_NAME, name);
        contentValues.put(COLUMN_QUANTITY, quantity);

        int updateStatus = db.update(TABLE_INVENTORY, contentValues, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return updateStatus > 0;
    }

    /**
     * Delete inventory item
     * @param id id of item being deleted
     * @return "true" if successful, "false" if failed
     */
    public boolean deleteInventoryItem(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deleteStatus = db.delete(TABLE_INVENTORY, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return deleteStatus > 0;
    }

    /**
     * Increment item quantity by one
     * @param id id of item being incremented
     */
    public void incrementItemQuantity(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COLUMN_QUANTITY + " = " + COLUMN_QUANTITY + " + 1 WHERE " + COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    /**
     * Decrement item quantity by one
     * @param id id of item being decremented
     */
    public void decrementItemQuantity(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COLUMN_QUANTITY + " = " + COLUMN_QUANTITY + " - 1 WHERE " + COLUMN_ID + " = ? AND " + COLUMN_QUANTITY + " > 0", new String[]{String.valueOf(id)});
        db.close();
    }
    //endregion
}