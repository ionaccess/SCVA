package com.SCVA.encryption;

public abstract class BaseEncryptor
{
	protected String key;
	protected byte[] key_in_bytes;

	BaseEncryptor(String key)
	{
		this.key = key;
		this.key_in_bytes = this.key.getBytes();
	}

	public abstract String encrypt(String plain);

	public abstract String decrypt(String cipher);

	public int getDecryptorChunkSize()
	{
		return this.encrypt("a").length();
	}

	public abstract int getEncryptorChunkSize();
}
