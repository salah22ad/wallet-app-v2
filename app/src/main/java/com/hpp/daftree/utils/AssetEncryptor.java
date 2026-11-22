package com.hpp.daftree.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;

public class AssetEncryptor {
    // مفتاح التشفير (يجب أن يكون 16 حرفاً - 128 bit)
    private static final String KEY = "DaftreeSecretKey"; // <-- غيّر هذا المفتاح

    public static void main(String[] args) {
        try {
            // قائمة ملفاتك التي تريد تشفيرها
            String[] files = {
                    "web_server_interface.html",
                    "styles_web_server.css",
                    "index.html",
                    "account_details.html",
                    "styles.css"
            };

            for (String fileName : files) {
                // حدد المسار الصحيح للملفات الأصلية على جهازك
                String inputPath = "path/to/your/assets/" + fileName;
                byte[] fileContent = Files.readAllBytes(Paths.get(inputPath));

                // التشفير
                byte[] encryptedContent = encrypt(fileContent);

                // حفظ الملف المشفر بامتداد جديد (مثلاً .secure)
                Files.write(Paths.get(inputPath + ".png"), encryptedContent);
                System.out.println("Encrypted: " + fileName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] encrypt(byte[] data) throws Exception {
        Key key = new SecretKeySpec(KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }
}
