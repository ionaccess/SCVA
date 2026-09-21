package com.SCVA.encryption;

import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BlowfishEncryptor extends BaseEncryptor{
    public BlowfishEncryptor(String key) {
        super(key);
    }

    @Override
    public String encrypt(String plain) {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append(plain);
            while (builder.length() % 8 != 0)
                builder.append(" ");
            return encrypt_blo(builder.toString());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String decrypt(String cipher) {
        try {
            return decrypt_blo(cipher);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public int getEncryptorChunkSize() {
        return 8;
    }

    private String encrypt_blo(String plain) throws
            NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException {
        SecretKeySpec KS = new SecretKeySpec(key_in_bytes, "Blowfish");
        Cipher cipher = Cipher.getInstance("Blowfish/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, KS,new IvParameterSpec(iv));
        byte [] enc = cipher.doFinal(plain.getBytes());
        return new String(Base64.encode(enc, Base64.DEFAULT));
    }

    private String decrypt_blo(String encryptedtext)
            throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException {
        SecretKeySpec KS = new SecretKeySpec(key_in_bytes, "Blowfish");
        Cipher cipher = Cipher.getInstance("Blowfish/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, KS,new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(Base64.decode(encryptedtext, Base64.DEFAULT));
        return new String(decrypted);
    }
    byte[] iv = "12345678".getBytes();
    
}
