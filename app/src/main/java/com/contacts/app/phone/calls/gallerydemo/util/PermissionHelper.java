package com.contacts.app.phone.calls.gallerydemo.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class PermissionHelper {
    public enum Access { FULL, PARTIAL, NONE }

    private PermissionHelper() {
    }

    @NonNull
    public  static String[] getRequestedPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            };

        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        }
        return new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE};
    }
    @NonNull
    public static String getPrimaryPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }


    @NonNull
    public static Access getAccess(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean images = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES);
            boolean videos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO);
            if (images || videos) {
                return Access.FULL;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
                return Access.PARTIAL;
            }
            return Access.NONE;
        }
        return isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                ? Access.FULL
                : Access.NONE;
    }

    private static boolean isGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
