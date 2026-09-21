package com.SCVA.encryption;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

import com.SCVA.Utils.Utils;

public class Aes256Encryptor extends BaseEncryptor
{
	byte[] IV = "1234567890987654".getBytes();

	public Aes256Encryptor(String key)
	{
		super(key);

		try
		{
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
			keyGenerator.init(256);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public String encrypt(String plain)
	{
		Cipher cipher = null;
		try
		{
			cipher = Cipher.getInstance("AES");
		}
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		catch (NoSuchPaddingException e)
		{
			e.printStackTrace();
		}
		SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
		IvParameterSpec ivSpec = new IvParameterSpec(IV);
		try
		{
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
		}
		catch (InvalidAlgorithmParameterException e)
		{
			e.printStackTrace();
		}
		catch (InvalidKeyException e)
		{
			e.printStackTrace();
		}
		byte[] cipherText = new byte[0];
		try
		{
			cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
		}
		catch (BadPaddingException e)
		{
			e.printStackTrace();
		}
		catch (IllegalBlockSizeException e)
		{
			e.printStackTrace();
		}
		return new String(Base64.encode(cipherText, Base64.DEFAULT));
	}

	@Override
	public String decrypt(String cipher)
	{
		Utils.println("AES decrypt cipher: ", cipher);
		try
		{
			Cipher cipher2 = Cipher.getInstance("AES");
			SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
			IvParameterSpec ivSpec = new IvParameterSpec(IV);
			cipher2.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
			byte[] decryptedText = cipher2.doFinal(Base64.decode(cipher, Base64.DEFAULT));
			Utils.println("AES decryptedText: ", new String(decryptedText));
			return new String(decryptedText);
		}
		catch (Exception e)
		{
			Utils.println("AES decrypt error: ", e.toString());
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int getEncryptorChunkSize()
	{
		return this.encrypt("a").length();
	}
}
