package com.contacts.app.phone.calls.gallerydemo.data;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

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

    private final Context context;
    private final ContentResolver contentResolver;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CallLogRepository(@NonNull Context context) {

        this.context = context.getApplicationContext();
        this.contentResolver = this.context.getContentResolver();
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

    // New Code
    // Simple container for contact details retrieved from ContactsContract
    public static class ContactInfo {
        @Nullable
        public final String name;
        @Nullable
        public final String lookupUri;

        public ContactInfo(@Nullable String name,
                           @Nullable String lookupUri) {
            this.name = name;
            this.lookupUri = lookupUri;
        }
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

                // Look up the real, current contact info from ContactsContract
                ContactInfo contactInfo = resolveContactInfo(number);

                String finalName = (contactInfo != null && contactInfo.name != null && !contactInfo.name.isEmpty())
                        ? contactInfo.name
                        : cachedName;
                String lookupUri = contactInfo != null ? contactInfo.lookupUri : null;

                items.add(new CallLogItem(id, finalName, number, type, date, duration, lookupUri));
            }
        }

        return items;
    }

    /**
     * Queries ContactsContract.PhoneLookup to find the real-time Contact Name and Lookup URI.
     */
    @androidx.annotation.Nullable
    private ContactInfo resolveContactInfo(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        // Safety check: ensure permission is granted before querying ContactsProvider
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, // or pass context into repository
                android.Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED) {
            return null; // Gracefully fallback to cachedName if permission isn't granted yet
        }

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
        );

        String[] projection = new String[]{
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup.DISPLAY_NAME
        };

        try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                int idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID);
                int lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY);

                String name = nameIndex != -1 ? cursor.getString(nameIndex) : null;
                long contactId = idIndex != -1 ? cursor.getLong(idIndex) : -1;
                String lookupKey = lookupKeyIndex != -1 ? cursor.getString(lookupKeyIndex) : null;

                String lookupUriString = null;
                if (contactId != -1 && lookupKey != null) {
                    android.net.Uri lookupUri = ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
                    if (lookupUri != null) {
                        lookupUriString = lookupUri.toString();
                    }
                }

                return new ContactInfo(name, lookupUriString);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error looking up contact for number: " + phoneNumber, e);
        }
        return null;
    }

    // Old Code
  /*  private List<CallLogItem> queryCallHistory() {
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
