package com.costoluce.importatore;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials are encrypted with a non-exportable AndroidKeyStore AES-GCM key, locally only. */
final class EnelCredentialStore {
    private static final String ALIAS = "costoluce.enel.v1";
    private static final String PREFS = "enel_secure_settings";
    private final SharedPreferences prefs;
    EnelCredentialStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    boolean hasSaved() { return prefs.contains("sealed") && prefs.contains("nonce"); }
    void clear() { prefs.edit().clear().apply(); }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    void save(EnelConnector.Credentials c) throws Exception {
        JSONObject json=new JSONObject();json.put("email",c.username);json.put("password",c.password);
        json.put("pod",c.pod);json.put("number",c.userNumber);json.put("cf",c.fiscalCode);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String sealed=Base64.encodeToString(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        String nonce=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        if(!prefs.edit().putString("sealed",sealed).putString("nonce",nonce).commit())throw new IOException("Impossibile salvare le credenziali.");
    }
    EnelConnector.Credentials read() throws Exception {
        if(!hasSaved())return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("nonce",""),Base64.NO_WRAP)));
        byte[] data=cipher.doFinal(Base64.decode(prefs.getString("sealed",""),Base64.NO_WRAP));
        JSONObject j=new JSONObject(new String(data,StandardCharsets.UTF_8));
        return new EnelConnector.Credentials(j.getString("email"),j.getString("password"),j.getString("pod"),j.getString("number"),j.optString("cf",""));
    }
}
