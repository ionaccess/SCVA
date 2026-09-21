package com.SCVA.encryption;

import android.util.Base64;

import com.SCVA.encryption.twofish.Twofish_Algorithm;

import java.security.InvalidKeyException;

public class TwofishEncryptor extends BaseEncryptor{
    public TwofishEncryptor(String key) {
        super(key);
    }

    @Override
    public String encrypt(String plain) {
        try {
            return new String(Base64.encode(ecbEncrypt(plain.getBytes(),key_in_bytes),Base64.DEFAULT));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public String decrypt(String cipher) {
        try {
            return new String(ecbDecrypt(Base64.decode(cipher,Base64.DEFAULT),key_in_bytes));
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public int getEncryptorChunkSize() {
        return 15;
    }
    
    byte[] ecbEncrypt(byte[] plaintext, byte[] key) throws InvalidKeyException {
        byte[] pt = pad(plaintext, 16);
        byte[] ciphertext = new byte[pt.length];
        for (int i = 0; i < pt.length; i += 16) {
            byte[] t = Twofish_Algorithm.blockEncrypt(pt, i, Twofish_Algorithm.makeKey(key));
            for (int j = i; j < i + 16; j++)
                ciphertext[j] = t[j % 16];
        }
        return ciphertext;
    }

    byte[] ecbDecrypt(byte[] ciphertext, byte[] key) throws InvalidKeyException {
        byte[] plaintext = new byte[ciphertext.length];
        for (int i = 0; i < ciphertext.length; i += 16) {
            byte[] t = Twofish_Algorithm.blockDecrypt(ciphertext, i, Twofish_Algorithm.makeKey(key));
            for (int j = i; j < i + 16; j++)
                plaintext[j] = t[j % 16];
        }
        return unpad(plaintext);
    }

    static byte[] pad(byte[] in, int blockSize) {
        byte[] ret = new byte[in.length + blockSize - in.length % blockSize];
        for (int i = 0; i < in.length; i++)
            ret[i] = in[i];
        byte paddedBytes = 0;
        for (int i = in.length; i < ret.length - 1; i++) {
            ret[i] = 0;
            paddedBytes++;
        }
        ret[ret.length - 1] = paddedBytes;
        return ret;
    }

    static byte[] unpad(byte[] in) {
        byte[] ret = new byte[in.length - in[in.length - 1] - 1];
        for (int i = 0; i < ret.length; i++)
            ret[i] = in[i];
        return ret;
    }
}
