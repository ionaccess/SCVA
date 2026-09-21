package com.SCVA.encryption;

import android.util.Base64;

import com.SCVA.encryption.serpent.SerpentOptimized;

public class SerpentEncryptor extends  BaseEncryptor{
    SerpentOptimized ser;

    public SerpentEncryptor(String key) {
        super(key);
        ser = new SerpentOptimized();
        ser.setKey(key_in_bytes);
    }

    @Override
    public String encrypt(String plain) {
        StringBuilder sp = new StringBuilder();
        sp.append(plain);
        while (sp.length() % 16 != 0)
            sp.append(" ");
        byte [] data = sp.toString().getBytes();
        ser.encrypt(data);
        return new String(Base64.encode(data,Base64.DEFAULT));
    }

    @Override
    public String decrypt(String cipher) {
        byte [] data = Base64.decode(cipher,Base64.DEFAULT);
        ser.decrypt(data);
        return new String(data);
    }

    @Override
    public int getEncryptorChunkSize() {
        return 25;
    }
}
