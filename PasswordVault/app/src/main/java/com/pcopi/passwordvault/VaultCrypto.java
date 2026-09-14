package com.pcopi.passwordvault;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class VaultCrypto {
    private static final int ITERATIONS = 150000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private VaultCrypto() {}

    public static String createSalt() {
        byte[] s = new byte[SALT_BYTES]; new SecureRandom().nextBytes(s);
        return Base64.encodeToString(s, Base64.NO_WRAP);
    }
    private static SecretKey key(String password, String salt) throws Exception {
        byte[] s = Base64.decode(salt, Base64.NO_WRAP);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), s, ITERATIONS, KEY_BITS);
        byte[] raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(raw, "AES");
    }
    public static String encrypt(String plain, String password, String salt) throws Exception {
        byte[] iv = new byte[IV_BYTES]; new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), new GCMParameterSpec(128, iv));
        byte[] data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + data.length]; System.arraycopy(iv,0,out,0,iv.length); System.arraycopy(data,0,out,iv.length,data.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }
    public static String decrypt(String encoded, String password, String salt) throws Exception {
        byte[] all = Base64.decode(encoded, Base64.NO_WRAP); if (all.length < IV_BYTES + 16) throw new Exception("ข้อมูลสำรองไม่ถูกต้อง");
        byte[] iv = Arrays.copyOfRange(all,0,IV_BYTES), data = Arrays.copyOfRange(all,IV_BYTES,all.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));
        return new String(c.doFinal(data), StandardCharsets.UTF_8);
    }
}
