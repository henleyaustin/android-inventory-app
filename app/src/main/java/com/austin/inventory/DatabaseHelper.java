package com.austin.inventory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "InventoryApp.db";
    private static final int DATABASE_VERSION = 4;

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
        db.execSQL("CREATE TABLE " + TABLE_USERS + "(" + COLUMN_EMAIL + " TEXT PRIMARY KEY, " + COLUMN_PASSWORD + " TEXT, " + COLUMN_PHONE + " TEXT, " + COLUMN_2FA_ENABLED + " INTEGER DEFAULT 0)"); // 0 for false, 1 for true

        // Create Inventory Table with a foreign key reference to Users Table
        db.execSQL("CREATE TABLE " + TABLE_INVENTORY + "(" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + COLUMN_NAME + " TEXT, " + COLUMN_QUANTITY + " INTEGER, " + COLUMN_USER_EMAIL + " TEXT, " + "FOREIGN KEY(" + COLUMN_USER_EMAIL + ") REFERENCES " + TABLE_USERS + "(" + COLUMN_EMAIL + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INVENTORY);
        // Create tables again
        onCreate(db);
    }

    // User-related operations

    public boolean insertUser(String email, String password, String phone) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_EMAIL, email);
        contentValues.put(COLUMN_PASSWORD, hashPassword(password));
        contentValues.put(COLUMN_PHONE, phone); // Add phone number to ContentValues
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

    public boolean checkUserCredentials(String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_USERS + " WHERE " + COLUMN_EMAIL + " = ? AND " + COLUMN_PASSWORD + " = ?", new String[]{email, hashPassword(password)});
        boolean valid = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return valid;
    }

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

    public void updateUser2FASetting(String email, boolean is2FAEnabled) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_2FA_ENABLED, is2FAEnabled ? 1 : 0); // Convert boolean to integer
        int numRowsUpdated = db.update(TABLE_USERS, contentValues, COLUMN_EMAIL + " = ?", new String[]{email});
        Log.d("DatabaseHelper", "Number of rows updated: " + numRowsUpdated); // Log to check if the update is successful
        db.close();
    }


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


    private String hashPassword(String password) {
        // Implement password hashing here
        return password; // Replace with actual hashed password
    }

    // Inventory-related operations

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


    public boolean updateInventoryItem(int id, String name, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_NAME, name);
        contentValues.put(COLUMN_QUANTITY, quantity);

        int updateStatus = db.update(TABLE_INVENTORY, contentValues, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return updateStatus > 0;
    }

    public boolean deleteInventoryItem(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int deleteStatus = db.delete(TABLE_INVENTORY, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return deleteStatus > 0;
    }

    public void incrementItemQuantity(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COLUMN_QUANTITY + " = " + COLUMN_QUANTITY + " + 1 WHERE " + COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public void decrementItemQuantity(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COLUMN_QUANTITY + " = " + COLUMN_QUANTITY + " - 1 WHERE " + COLUMN_ID + " = ? AND " + COLUMN_QUANTITY + " > 0", new String[]{String.valueOf(id)});
        db.close();
    }
}