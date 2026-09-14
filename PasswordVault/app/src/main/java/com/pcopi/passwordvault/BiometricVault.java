package com.pcopi.passwordvault;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BiometricVault {
    private static final String KS="AndroidKeyStore",ALIAS="PrivateAppBiometricKey",PREF="biometric",ENABLED="enabled",IV="iv",DATA="data";
    private final Context context; private final SharedPreferences prefs;
    public BiometricVault(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public boolean enabled(){return prefs.getBoolean(ENABLED,false)&&prefs.contains(DATA)&&prefs.contains(IV);}
    public boolean canUse(){if(Build.VERSION.SDK_INT<28)return false;try{return context.getPackageManager().hasSystemFeature("android.hardware.fingerprint")||context.getPackageManager().hasSystemFeature("android.hardware.biometrics");}catch(Exception e){return false;}}
    private SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance(KS);ks.load(null);return(SecretKey)ks.getKey(ALIAS,null);}
    private void createKey()throws Exception{KeyStore ks=KeyStore.getInstance(KS);ks.load(null);if(ks.containsAlias(ALIAS))ks.deleteEntry(ALIAS);KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);KeyGenParameterSpec.Builder b=new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true);if(Build.VERSION.SDK_INT>=30)b.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG);else b.setUserAuthenticationValidityDurationSeconds(-1);kg.init(b.build());kg.generateKey();}
    private Cipher cipher(int mode,byte[] iv)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");if(iv==null)c.init(mode,key());else c.init(mode,key(),new GCMParameterSpec(128,iv));return c;}
    public void enable(final Activity a,final String master,final Runnable ok,final Runnable fail){if(!canUse()){fail.run();return;}try{createKey();final Cipher c=cipher(Cipher.ENCRYPT_MODE,null);authenticate(a,c,new Result(){public void success(Cipher done){try{byte[] data=done.doFinal(master.getBytes(StandardCharsets.UTF_8));prefs.edit().putBoolean(ENABLED,true).putString(IV,Base64.encodeToString(done.getIV(),Base64.NO_WRAP)).putString(DATA,Base64.encodeToString(data,Base64.NO_WRAP)).apply();ok.run();}catch(Exception e){prefs.edit().clear().apply();fail.run();}}public void error(){prefs.edit().clear().apply();fail.run();}});}catch(Exception e){prefs.edit().clear().apply();fail.run();}}
    public void disable(){prefs.edit().clear().apply();try{KeyStore ks=KeyStore.getInstance(KS);ks.load(null);if(ks.containsAlias(ALIAS))ks.deleteEntry(ALIAS);}catch(Exception ignored){}}
    public void unlock(final Activity a,final MasterCallback ok,final Runnable fail){if(!enabled()){fail.run();return;}try{byte[] iv=Base64.decode(prefs.getString(IV,""),Base64.NO_WRAP);final Cipher c=cipher(Cipher.DECRYPT_MODE,iv);authenticate(a,c,new Result(){public void success(Cipher done){try{String m=new String(done.doFinal(Base64.decode(prefs.getString(DATA,""),Base64.NO_WRAP)),StandardCharsets.UTF_8);if(m.isEmpty())throw new Exception();ok.run(m);}catch(Exception e){fail.run();}}public void error(){fail.run();}});}catch(Exception e){fail.run();}}
    public interface MasterCallback extends Runnable{void run(String master);@Override default void run(){}}
    private interface Result{void success(Cipher c);void error();}
    private void authenticate(Activity a,final Cipher cipher,final Result result){if(Build.VERSION.SDK_INT<28){result.error();return;}Executor ex=a.getMainExecutor();BiometricPrompt.Builder b=new BiometricPrompt.Builder(a).setTitle("ส่วนตัว").setSubtitle("ยืนยันลายนิ้วมือเพื่อเข้าใช้งาน").setDescription("ข้อมูลส่วนตัวจะยังคงเข้ารหัสอยู่ในเครื่อง");b.setNegativeButton("ใช้ Password",ex,(DialogInterface d,int w)->result.error());BiometricPrompt prompt=b.build();prompt.authenticate(new BiometricPrompt.CryptoObject(cipher),new android.os.CancellationSignal(),ex,new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){result.success(r.getCryptoObject().getCipher());}@Override public void onAuthenticationError(int code,CharSequence msg){result.error();}});}
}
