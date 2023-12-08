package com.austin.inventory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "InventoryApp.db";
    private static final int DATABASE_VERSION = 1;

    // Users table
    private static final String TABLE_USERS = "allusers";
    private static final String COLUMN_EMAIL = "email";
    private static final String COLUMN_PASSWORD = "password";
    private static final String COLUMN_PHONE = "phone";


    // Inventory table
    private static final String TABLE_INVENTORY = "inventory";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_QUANTITY = "quantity";

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USERS + "(" +
                COLUMN_EMAIL + " TEXT PRIMARY KEY, " +
                COLUMN_PASSWORD + " TEXT, " +
                COLUMN_PHONE + " TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_INVENTORY + "(" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_QUANTITY + " INTEGER)");
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

    private String hashPassword(String password) {
        // Implement password hashing here
        return password; // Replace with actual hashed password
    }

    // Inventory-related operations

    public boolean insertInventoryItem(String name, int quantity) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_NAME, name);
        contentValues.put(COLUMN_QUANTITY, quantity);
        long result = db.insert(TABLE_INVENTORY, null, contentValues);
        db.close();
        return result != -1;
    }

    public List<InventoryItem> getAllInventoryItems() {
        List<InventoryItem> itemList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_INVENTORY, null);

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

    public boolean incrementItemQuantity(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COLUMN_QUANTITY + " = " + COLUMN_QUANTITY + " + 1 WHERE " + COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return true;
    }

    public boolean decrementItemQuantity(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_INVENTORY + " SET " + COLUMN_QUANTITY + " = " + COLUMN_QUANTITY + " - 1 WHERE " + COLUMN_ID + " = ? AND " + COLUMN_QUANTITY + " > 0", new String[]{String.valueOf(id)});
        db.close();
        return true;
    }


    public int getNextItemId() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + TABLE_INVENTORY, null);
        int nextId = 1; // Default to 1 if no items are present
        if (cursor.moveToFirst()) {
            nextId = cursor.getInt(0) + 1; // Add 1 to the highest ID
        }
        cursor.close();
        db.close();
        return nextId;
    }
}