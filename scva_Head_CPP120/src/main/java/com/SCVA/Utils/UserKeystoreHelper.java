package com.SCVA.Utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Multi-user Android Keystore Helper
 *
 * One AES key per user identity.
 *
 * Example: user_1001 -> separate keystore key user_1002 -> separate keystore
 * key
 *
 * Key Alias Format: USER_KEY_<identity>
 */
/**
 * Created by §∞§ on 30/04/2026.
 */
public class UserKeystoreHelper
{
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String KEY_PREFIX = "USER_KEY_";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	/**
	 * Generates unique alias for user identity
	 */
	private static String getAlias(String identity)
	{
		if (identity == null || identity.trim().isEmpty())
		{
			throw new IllegalArgumentException("Identity cannot be null or empty");
		}
		return KEY_PREFIX + identity;
	}

	/**
	 * Create user-specific key if not exists
	 */
	public static void generateKey(String identity) throws Exception
	{
		String alias = getAlias(identity);
		KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
		keyStore.load(null);

		if (keyStore.containsAlias(alias))
		{
			return;
		}

		KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
		KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setKeySize(AES_KEY_SIZE)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true)

				// Optional:
				// Require device unlock
				// .setUserAuthenticationRequired(true)

				.build();
		keyGenerator.init(keySpec);
		keyGenerator.generateKey();
	}

	/**
	 * Get user-specific key
	 */
	private static SecretKey getSecretKey(String identity) throws Exception
	{
		String alias = getAlias(identity);
		KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
		keyStore.load(null);
		return (SecretKey) keyStore.getKey(alias, null);
	}

	/**
	 * Encrypt text for specific user identity
	 */
	public static String encrypt(String identity, String plainText) throws Exception
	{
		generateKey(identity);
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(identity));
		byte[] iv = cipher.getIV();
		byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
		String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);
		String encryptedBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
		return ivBase64 + ":" + encryptedBase64;
	}

	/**
	 * Decrypt text for specific user identity
	 */
	public static String decrypt(String identity, String encryptedData) throws Exception
	{
		generateKey(identity);
		String[] parts = encryptedData.split(":");
		if (parts.length != 2)
		{
			throw new IllegalArgumentException("Invalid encrypted format");
		}
		byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
		byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
		cipher.init(Cipher.DECRYPT_MODE, getSecretKey(identity), spec);
		byte[] decrypted = cipher.doFinal(encrypted);
		return new String(decrypted, StandardCharsets.UTF_8);
	}

	/**
	 * Delete user key
	 */
	public static void deleteKey(String identity) throws Exception
	{
		String alias = getAlias(identity);
		KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
		keyStore.load(null);
		if (keyStore.containsAlias(alias))
		{
			keyStore.deleteEntry(alias);
		}
	}

	/**
	 * Check if user key exists
	 */
	public static boolean keyExists(String identity) throws Exception
	{
		String alias = getAlias(identity);
		KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
		keyStore.load(null);
		return keyStore.containsAlias(alias);
	}

	public static String encryptLongText(String identity, String plainText) throws Exception
	{
		generateKey(identity);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(identity));
		byte[] iv = cipher.getIV();
		byte[] inputBytes = plainText.getBytes(StandardCharsets.UTF_8);
		byte[] encryptedBytes = cipher.doFinal(inputBytes);
		// Combine IV + encrypted data
		byte[] combined = new byte[iv.length + encryptedBytes.length];
		System.arraycopy(iv, 0, combined, 0, iv.length);
		System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
		return Base64.encodeToString(combined, Base64.NO_WRAP);
	}

	public static String decryptLongText(String identity, String encryptedData) throws Exception
	{
		generateKey(identity);
		byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
		// GCM standard IV length = 12 bytes
		byte[] iv = new byte[12];
		byte[] encryptedBytes = new byte[combined.length - 12];
		System.arraycopy(combined, 0, iv, 0, 12);
		System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.length);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec spec = new GCMParameterSpec(128, iv);
		cipher.init(Cipher.DECRYPT_MODE, getSecretKey(identity), spec);
		byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
		return new String(decryptedBytes, StandardCharsets.UTF_8);
	}
}