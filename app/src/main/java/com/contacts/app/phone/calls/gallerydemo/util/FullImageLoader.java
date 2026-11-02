package com.contacts.app.phone.calls.gallerydemo.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

// Never loads a full-resolution photo. The picture is shrunk while decoding so memory stays small.
public final class FullImageLoader {

    private FullImageLoader() {
    }

    @NonNull
    public static Bitmap decode(@NonNull ContentResolver resolver, @NonNull Uri uri, int maxDimension)
            throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeWithImageDecoder(resolver, uri, maxDimension);
        }
        return decodeWithBitmapFactory(resolver, uri, maxDimension);
    }

    // Android 9+: ImageDecoder shrinks the image while decoding AND applies the camera rotation
    @RequiresApi(Build.VERSION_CODES.P)
    @NonNull
    private static Bitmap decodeWithImageDecoder(ContentResolver resolver, Uri uri, int maxDimension)
            throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int largest = Math.max(width, height);
            if (largest > maxDimension) {
                float scale = (float) maxDimension / largest;
                decoder.setTargetSize(
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)));
            }
        });
    }

    // Android 8: BitmapFactory with inSampleSize, then rotate using the EXIF orientation tag
    @NonNull
    private static Bitmap decodeWithBitmapFactory(ContentResolver resolver, Uri uri, int maxDimension)
            throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new FileNotFoundException(uri.toString());
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }

        int sampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new FileNotFoundException(uri.toString());
            }
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Could not decode " + uri);
        }
        return rotateIfNeeded(resolver, uri, bitmap);
    }

    @NonNull
    private static Bitmap rotateIfNeeded(ContentResolver resolver, Uri uri, Bitmap bitmap) {
        int orientation;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return bitmap;
            }
            orientation = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            return bitmap;
        }

        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            default:
                return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}