package com.contacts.app.phone.calls.gallerydemo.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.model.CallLogItem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CallLogRepository {
    private static final String TAG = "CallLogRepository";

    public interface LoadCallback {
        void onLoaded(@NonNull List<CallLogItem> items);
        void onError(@NonNull Exception error);
    }
    public interface DeleteCallback {
        void onSuccess();
        void onError(@NonNull Exception error);
    }
    /**
     * Deletes a specific call record from the system database using its unique ID.
     */
    public void deleteCallRecord(long recordId, @NonNull DeleteCallback callback) {
        executor.execute(() -> {
            try {
                // Where clause: "_id = ?" targeting the exact record ID
                String where = CallLog.Calls._ID + " = ?";
                String[] selectionArgs = new String[]{ String.valueOf(recordId) };
                int rowsDeleted = contentResolver.delete(
                        CallLog.Calls.CONTENT_URI,
                        where,
                        selectionArgs
                );
                if (rowsDeleted > 0) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("Record not found or already deleted")));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete call record", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }


    // The specific database columns we want to fetch
    private static final String[] PROJECTION = {
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
    };

    private final ContentResolver contentResolver;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CallLogRepository(@NonNull Context context) {
        this.contentResolver = context.getApplicationContext().getContentResolver();
    }
    /**
     * Loads calls in the background and delivers the result on the main UI thread.
     */

    public void loadCallHistory(@NonNull LoadCallback callback) {
        executor.execute(() -> {
            try {
                List<CallLogItem> items = queryCallHistory();
                mainHandler.post(() -> callback.onLoaded(items));
            } catch (Exception e) {
                Log.e(TAG, "Error loading call logs", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private List<CallLogItem> queryCallHistory() {
        List<CallLogItem> items = new ArrayList<>();
        String sortOrder = CallLog.Calls.DATE + " DESC";
        try (Cursor cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                null,
                null,
                sortOrder
        )) {
            if (cursor == null) {
                return items;
            }
            int idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME);
            int numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            int typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE);
            int durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String cachedName = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);
                int type = cursor.getInt(typeIndex);
                long date = cursor.getLong(dateIndex);
                long duration = cursor.getLong(durationIndex);
                // Check for real-time contact name from PhoneLookup
                String realTimeName = resolveContactName(number);
                String finalName = (realTimeName != null && !realTimeName.isEmpty())
                        ? realTimeName
                        : cachedName;
                items.add(new CallLogItem(id, finalName, number, type, date, duration));
            }
        }
        return items;
    }
   /* private List<CallLogItem> queryCallHistory() {
        List<CallLogItem> items = new ArrayList<>();
        // Sort by Date in descending order (newest calls first)
        String sortOrder = CallLog.Calls.DATE + " DESC";

        try (Cursor cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                PROJECTION,
                null,
                null,
                sortOrder
        )) {
            if (cursor == null) {
                return  items;
            }
            int idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME);
            int numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            int typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE);
            int dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE);
            int durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                String cachedName = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);
                int type = cursor.getInt(typeIndex);
                long date = cursor.getLong(dateIndex);
                long duration = cursor.getLong(durationIndex);

                // Check for real-time contact name from PhoneLookup
                String realTimeName = resolveContactName(number);
                String finalName = (realTimeName != null && !realTimeName.isEmpty())
                        ? realTimeName
                        :cachedName;

                items.add(new CallLogItem(id, finalName, number, type, date, duration));
            }
        }
        return items;
    }*/

    /**
     * Looks up the most current contact name for a phone number directly from Contacts.
     */
    @androidx.annotation.Nullable
    private String resolveContactName(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        android.net.Uri uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
        );
        String[] projection = new String[]{
                android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME
        };
        try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME);
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            // If contact permission is not available or query fails, fallback gracefully

        }
        return null;
    }

    public void close() {
        executor.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
