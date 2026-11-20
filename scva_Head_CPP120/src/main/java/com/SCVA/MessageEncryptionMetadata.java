package com.SCVA;

import com.SCVA.encryption.*;
import com.SCVA.models.TextMessage;
import java.util.ArrayList;
import java.util.List;

/** Local storage metadata only. No passwords, unlocked material, wire changes or cipher implementation. */
public final class MessageEncryptionMetadata {
    public static final String PLAINTEXT="PLAINTEXT", CHUNKS="SCVA_CHUNKS", MODEM73="SCM73E";
    private MessageEncryptionMetadata() { }
    public static boolean knownAlgorithm(String value) {
        return "aes".equals(value)||"rc6".equals(value)||"twofish".equals(value)||"serpent".equals(value)
                ||"gost28147".equals(value)||"blowfish".equals(value);
    }
    public static String algorithmOf(BaseEncryptor cipher) {
        if(cipher instanceof Aes256Encryptor)return "aes";
        if(cipher instanceof Rc6Encryptor)return "rc6";
        if(cipher instanceof TwofishEncryptor)return "twofish";
        if(cipher instanceof SerpentEncryptor)return "serpent";
        if(cipher instanceof Gost28147Encryptor)return "gost28147";
        if(cipher instanceof BlowfishEncryptor)return "blowfish";
        throw new IllegalStateException("The encryption context is not ready");
    }
    public static String problem(boolean encrypted,int contactId,int keyId,String algorithm,String format,int version,String parameters) {
        if(contactId<=0 || keyId<0)return "inconsistent_metadata";
        if(algorithm==null || format==null || parameters==null)return "missing_metadata";
        if(!encrypted)return algorithm.isEmpty()&&PLAINTEXT.equals(format)&&version==0&&parameters.isEmpty()?null:"inconsistent_metadata";
        if(algorithm.isEmpty() || format.isEmpty() || keyId<=0)return "missing_metadata";
        if(!knownAlgorithm(algorithm) || !CHUNKS.equals(format)&&!MODEM73.equals(format) || version!=1)return "unsupported_metadata";
        if(MODEM73.equals(format)&&(!Modem73Crypto.supports(algorithm)||!parameters.isEmpty()))return "inconsistent_metadata";
        if(CHUNKS.equals(format)&&!parameters.isEmpty()&&!parameters.startsWith("U16:"))return "inconsistent_metadata";
        return null;
    }
    public static final class Snapshot {
        public final boolean encrypted;
        public final int contactId,keyId,version;
        public final String algorithm,format,parameters;
        private Snapshot(boolean encrypted,int contactId,int keyId,String algorithm,String format,int version,String parameters) {
            String error=problem(encrypted,contactId,keyId,algorithm,format,version,parameters);
            if(error!=null)throw new IllegalArgumentException("Message encryption metadata: "+error);
            this.encrypted=encrypted;this.contactId=contactId;this.keyId=keyId;this.algorithm=algorithm;
            this.format=format;this.version=version;this.parameters=parameters;
        }
        public void applyTo(TextMessage row) {
            row.setContactId(contactId);row.setKeyId(keyId);row.setEncrypted(encrypted);
            row.setEncryptionAlgorithm(algorithm);row.setCiphertextFormat(format);row.setCiphertextVersion(version);row.setEncryptionParameters(parameters);
        }
    }
    public static Snapshot plaintext(int contactId,int keyId) { return new Snapshot(false,contactId,keyId,"",PLAINTEXT,0,""); }
    public static Snapshot chunks(int contactId,int keyId,String algorithm,String parameters) { return new Snapshot(true,contactId,keyId,algorithm,CHUNKS,1,parameters); }
    public static Snapshot modem73(int contactId,int keyId,String algorithm) { return new Snapshot(true,contactId,keyId,algorithm,MODEM73,1,""); }
    public static Snapshot from(TextMessage row) { return new Snapshot(row.isEncrypted(),row.getContactId(),row.getKeyId(),row.getEncryptionAlgorithm(),row.getCiphertextFormat(),row.getCiphertextVersion(),row.getEncryptionParameters()); }

    /** Encoded UTF-16 character count / original UTF-16 count for each actually encrypted TX chunk. */
    public static final class ChunkLedger {
        private final StringBuilder values=new StringBuilder();
        public void add(String transformed,int originalUnits) {
            if(transformed==null||transformed.isEmpty()||originalUnits<1)throw new IllegalArgumentException("Invalid prepared encryption chunk");
            if(values.length()>0)values.append(',');values.append(transformed.length()).append('/').append(originalUnits);
        }
        public String parameters() { return values.length()==0?"":"U16:"+values; }
    }
    public static final class Chunk {
        public final String ciphertext;public final int originalUnits;
        private Chunk(String ciphertext,int originalUnits) { this.ciphertext=ciphertext;this.originalUnits=originalUnits; }
    }
    public static List<Chunk> chunksWithLengths(String stored,String parameters) {
        if(stored==null||parameters==null||!parameters.startsWith("U16:"))throw new IllegalArgumentException("Invalid encryption chunk metadata");
        String body=stored.endsWith("§")?stored.substring(0,stored.length()-1):stored;
        List<Chunk> chunks=new ArrayList<>();int offset=0;
        for(String entry:parameters.substring(4).split(",",-1)) {
            if(!entry.matches("[1-9][0-9]*/[1-9][0-9]*"))throw new IllegalArgumentException("Invalid encryption chunk metadata");
            String[] sizes=entry.split("/");int encoded=Integer.parseInt(sizes[0]),units=Integer.parseInt(sizes[1]);
            if(encoded>body.length()-offset)throw new IllegalArgumentException("Encryption chunk metadata length mismatch");
            String chunk=body.substring(offset,offset+encoded);
            if(!chunk.endsWith("¢")||!chunk.replace("¢","").matches("[A-Za-z0-9+/]+={0,2}"))throw new IllegalArgumentException("Invalid stored encryption chunk");
            chunks.add(new Chunk(chunk,units));offset+=encoded;
        }
        if(offset!=body.length()||chunks.isEmpty())throw new IllegalArgumentException("Encryption chunk metadata length mismatch");
        return chunks;
    }
    /** This is only a strict local legacy-format candidate test, not algorithm discovery/authentication. */
    public static boolean looksLikeChunks(String text) {
        if(text==null)return false;String body=text.endsWith("§")?text.substring(0,text.length()-1):text;
        return (body.contains("¢")||body.contains("\n"))&&body.matches("[A-Za-z0-9+/=¢\\r\\n]+")
                &&body.replace("¢","").replace("\r","").replace("\n","").length()>=12;
    }
    public static void validateStored(TextMessage row) {
        Snapshot metadata=from(row);
        if(!metadata.encrypted)return;
        if(MODEM73.equals(metadata.format)) {
            if(!Modem73Crypto.stored(row.getMessage()).algorithm.equals(metadata.algorithm))throw new IllegalArgumentException("Stored encryption algorithm mismatch");
        } else {
            if(!looksLikeChunks(row.getMessage()) || row.getMessage().startsWith("SCM73E"))throw new IllegalArgumentException("Invalid ciphertext storage format");
            if(!metadata.parameters.isEmpty())chunksWithLengths(row.getMessage(),metadata.parameters);
        }
    }
    public static void validatePrepared(Snapshot metadata,String stored) {
        TextMessage row=new TextMessage();metadata.applyTo(row);row.setMessage(stored);validateStored(row);
    }
    public static final class Delivery<T> {
        public final T content;public final Snapshot metadata;
        public Delivery(T content,Snapshot metadata) { this.content=content;this.metadata=metadata; }
    }
}
