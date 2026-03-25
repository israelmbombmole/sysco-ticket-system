package com.app.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtil {

    private static final String SECRET = "1234567890123456"; // 16 chars (AES-128)

    private static final SecretKeySpec key =
            new SecretKeySpec(SECRET.getBytes(), "AES");

    public static String encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(strToEncrypt.getBytes())
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(
                    cipher.doFinal(Base64.getDecoder().decode(strToDecrypt))
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}