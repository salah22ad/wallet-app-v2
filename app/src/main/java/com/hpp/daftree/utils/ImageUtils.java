package com.hpp.daftree.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;
import java.io.ByteArrayOutputStream;

public class ImageUtils {

    // الحد الأقصى للحجم (الكيلوبايت)
    private static final int MAX_SIZE_KB = 200; // تخفيض من 200 إلى 150
    // الحد الأقصى للأبعاد
    private static final int MAX_DIMENSION = 2048; // تخفيض من 2048 إلى 1200

    /**
     * دالة محسنة وسريعة لتحويل Bitmap إلى Base64
     * تعمل بشكل غير متزامن (يجب استدعاؤها في خيط خلفي)
     */
    public static String bitmapToBase64(Context context, Bitmap bitmap) {
        if (bitmap == null || context == null) return null;

        // 1. تصغير الأبعاد أولاً (أسرع عملية)
        Bitmap scaledBitmap = getOptimizedScaledBitmap(bitmap, MAX_DIMENSION);

        // 2. ضغط مباشر بدون معالجة RenderScript الثقيلة
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        // استخدام WebP لأنه أسرع وأفضل جودة
        Bitmap.CompressFormat compressFormat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ?
                Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.JPEG;

        // جودة متوسطة للتوازن بين السرعة والجودة
        int quality = 75;

        // ضغط مباشر
        scaledBitmap.compress(compressFormat, quality, stream);

        byte[] byteArray = stream.toByteArray();

        // 3. إذا كان الحجم كبيراً جداً، نعيد الضغط بجودة أقل
        int iterations = 0;
        while (byteArray.length / 1024 > MAX_SIZE_KB && quality > 40 && iterations < 3) {
            stream.reset();
            quality -= 15; // تقليل الجودة بشكل أكبر
            scaledBitmap.compress(compressFormat, quality, stream);
            byteArray = stream.toByteArray();
            iterations++;
        }

        // تنظيف الذاكرة
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }

        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    /**
     * دالة سريعة لتحجيم الصورة مع الحفاظ على التناسب
     */
    private static Bitmap getOptimizedScaledBitmap(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // إذا كانت الأبعاد ضمن الحدود، نعيد نفس الصورة
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap;
        }

        float aspectRatio = (float) width / (float) height;
        int newWidth, newHeight;

        if (width > height) {
            newWidth = maxDimension;
            newHeight = (int) (maxDimension / aspectRatio);
        } else {
            newHeight = maxDimension;
            newWidth = (int) (maxDimension * aspectRatio);
        }

        // استخدام createScaledBitmap السريع
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    /**
     * دالة سريعة لتحويل Base64 إلى Bitmap
     */
    public static Bitmap base64ToBitmap(String base64Str) {
        if (base64Str == null || base64Str.isEmpty()) return null;

        try {
            byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);

            // خيارات لتقليل استخدام الذاكرة
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1; // لا نستخدم sample لأن الصورة مضغوطة بالفعل
            options.inPreferredConfig = Bitmap.Config.RGB_565; // توفير في الذاكرة

            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, options);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * دالة للصور الصغيرة (مثل الأيقونات)
     */
    public static String bitmapToBase64Small(Bitmap bitmap) {
        if (bitmap == null) return null;

        // تحجيم للصورة الصغيرة
        Bitmap smallBitmap = getOptimizedScaledBitmap(bitmap, 800);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        smallBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);

        if (smallBitmap != bitmap) {
            smallBitmap.recycle();
        }

        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
    }
}