package com.SCVA;

import android.util.Base64;
import com.SCVA.encryption.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Read-only history interpretation. No modem/configuration, database writes or global key state. */
public final class EncryptedMessageHistory {
    private EncryptedMessageHistory() { }

    public enum Status {
        PLAINTEXT, OPENED, OPENED_LEGACY_PADDING, LOCKED_KEY, MISSING_KEY,
        MISSING_ALGORITHM, MISSING_METADATA, INVALID_METADATA, MISSING_REFERENCED_KEY,
        UNSUPPORTED_FORMAT, MALFORMED_RECORD, DECRYPTION_FAILED, LEGACY_BOUNDARY_AMBIGUITY, STALE_CONTEXT
    }
    public static final class Row {
        public final int id, contactId, keyId;
        public final boolean encrypted;
        public final String stored;
        public final boolean metadataPresent;
        public final String algorithm,storageFormat,parameters;
        public final int version;
        public Row(int id, int contactId, int keyId, boolean encrypted, String stored) {
            this.id=id; this.contactId=contactId; this.keyId=keyId;
            this.encrypted=encrypted; this.stored=stored;
            metadataPresent=false;algorithm=null;storageFormat=null;parameters=null;version=-1;
        }
        public Row(int id,int contactId,int keyId,boolean encrypted,String stored,String algorithm,String format,int version,String parameters) {
            this.id=id;this.contactId=contactId;this.keyId=keyId;this.encrypted=encrypted;this.stored=stored;
            metadataPresent=true;this.algorithm=algorithm;storageFormat=format;this.version=version;this.parameters=parameters;
        }
        private boolean same(Row other) {
            return other!=null && id==other.id && contactId==other.contactId && keyId==other.keyId
                    && encrypted==other.encrypted && Objects.equals(stored,other.stored) && metadataPresent==other.metadataPresent
                    && version==other.version && Objects.equals(algorithm,other.algorithm) && Objects.equals(storageFormat,other.storageFormat)
                    && Objects.equals(parameters,other.parameters);
        }
    }
    /** A reference from the intended contact's key list, never from a process-wide active key. */
    public static final class KeyRef {
        public final int id, contactId;
        private final String wrapped;
        public KeyRef(int id,int contactId,String wrapped) {
            this.id=id; this.contactId=contactId; this.wrapped=wrapped;
        }
        private boolean same(KeyRef other) {
            return other!=null && id==other.id && contactId==other.contactId && Objects.equals(wrapped,other.wrapped);
        }
    }
    public static final class Result {
        public final Status status;
        public final String text;
        private Result(Status status,String text) { this.status=status; this.text=text; }
        public boolean opened() { return status==Status.OPENED || status==Status.OPENED_LEGACY_PADDING; }
        public String display() {
            switch(status) {
                case PLAINTEXT: case OPENED: case OPENED_LEGACY_PADDING: return text;
                case LOCKED_KEY: return "[Encrypted history: key locked — tap to unlock]";
                case MISSING_KEY: return "[Encrypted history: referenced key missing — tap to select a contact key]";
                case MISSING_ALGORITHM: return "[Encrypted history: legacy algorithm not stored — tap to choose]";
                case MISSING_METADATA: return "[Encrypted history: required message metadata missing]";
                case INVALID_METADATA: return "[Encrypted history: inconsistent message metadata]";
                case MISSING_REFERENCED_KEY: return "[Encrypted history: referenced contact key unavailable]";
                case UNSUPPORTED_FORMAT: return "[Encrypted history: unsupported format or algorithm]";
                case MALFORMED_RECORD: return "[Encrypted history: malformed encrypted record]";
                case DECRYPTION_FAILED: return "[Encrypted history: decryption failed]";
                case LEGACY_BOUNDARY_AMBIGUITY: return "[Encrypted history: legacy Serpent chunk boundaries are ambiguous]";
                default: return "[Encrypted history: chat context changed]";
            }
        }
    }
    private static Result failure(Status status) { return new Result(status,null); }
    private static final class Description {
        Modem73Crypto.Encrypted modern;
        Status error;
    }
    private static Description describe(Row row) {
        Description d=new Description();
        if(row.metadataPresent) {
            String problem=MessageEncryptionMetadata.problem(row.encrypted,row.contactId,row.keyId,row.algorithm,row.storageFormat,row.version,row.parameters);
            if(problem!=null) { d.error="missing_metadata".equals(problem)?Status.MISSING_METADATA:"unsupported_metadata".equals(problem)?Status.UNSUPPORTED_FORMAT:Status.INVALID_METADATA;return d; }
            if(!row.encrypted)return d;
            if(MessageEncryptionMetadata.MODEM73.equals(row.storageFormat) && (row.stored==null||!row.stored.startsWith(Modem73Crypto.STORED_PREFIX))) {d.error=Status.MALFORMED_RECORD;return d;}
            if(MessageEncryptionMetadata.CHUNKS.equals(row.storageFormat) && row.stored!=null && row.stored.startsWith("SCM73E")) {d.error=Status.INVALID_METADATA;return d;}
        }
        if(!row.encrypted)return d;
        if(row.stored==null || row.stored.isEmpty()) { d.error=Status.MALFORMED_RECORD; return d; }
        // Reserve the explicit modern family, including future storage versions. Never retry as legacy.
        if(row.stored.startsWith("SCM73E")) {
            if(!row.stored.startsWith(Modem73Crypto.STORED_PREFIX)) d.error=Status.UNSUPPORTED_FORMAT;
            else try {
                if(row.stored.length()>2800) { d.error=Status.MALFORMED_RECORD; return d; }
                String body=row.stored.substring(Modem73Crypto.STORED_PREFIX.length());
                if(!body.matches("[A-Za-z0-9+/]+={0,2}")) { d.error=Status.MALFORMED_RECORD; return d; }
                byte[] wire=Base64.decode(body,Base64.NO_WRAP);
                if(Modem73Crypto.claims(wire) && wire.length>=9 && (wire[6]!=1 || wire[7]!=1
                        || wire[8]!=1 && wire[8]!=2 && wire[8]!=3 && wire[8]!=5)) {
                    d.error=Status.UNSUPPORTED_FORMAT; return d;
                }
                d.modern=Modem73Crypto.stored(row.stored);
                if(row.metadataPresent && !d.modern.algorithm.equals(row.algorithm))d.error=Status.INVALID_METADATA;
            }
            catch(RuntimeException malformed) { d.error=Status.MALFORMED_RECORD; }
        }
        else {
            String body=row.stored.endsWith("§")?row.stored.substring(0,row.stored.length()-1):row.stored;
            if(body.isEmpty() || !body.matches("[A-Za-z0-9+/=¢\\r\\n]+")) d.error=Status.UNSUPPORTED_FORMAT;
            if(row.metadataPresent && d.error==null && !row.parameters.isEmpty())
                try { MessageEncryptionMetadata.chunksWithLengths(row.stored,row.parameters); }
                catch(RuntimeException invalid) { d.error=Status.INVALID_METADATA; }
        }
        return d;
    }
    public static boolean isModern(Row row) { return row.encrypted && row.stored!=null && row.stored.startsWith("SCM73E"); }
    public static boolean legacyAlgorithmSupported(String algorithm) {
        return "aes".equals(algorithm) || "rc6".equals(algorithm) || "twofish".equals(algorithm)
                || "serpent".equals(algorithm) || "gost28147".equals(algorithm) || "blowfish".equals(algorithm);
    }
    private static BaseEncryptor legacyCipher(String algorithm,String material) {
        switch(algorithm) {
            case "aes": return new Aes256Encryptor(material);
            case "rc6": return new Rc6Encryptor(material);
            case "twofish": return new TwofishEncryptor(material);
            case "serpent": return new SerpentEncryptor(material); // Read only; MODEM73 TX remains excluded.
            case "gost28147": return new Gost28147Encryptor(material);
            case "blowfish": return new BlowfishEncryptor(material);
            default: throw new IllegalArgumentException("unsupported history algorithm");
        }
    }
    private static final class AmbiguousLegacyBoundary extends IllegalArgumentException { }
    private static String decryptRecordedChunks(Row row,String material) {
        BaseEncryptor cipher=legacyCipher(row.algorithm,material);StringBuilder plain=new StringBuilder();
        for(MessageEncryptionMetadata.Chunk chunk:MessageEncryptionMetadata.chunksWithLengths(row.stored,row.parameters)) {
            String text=cipher.decrypt(chunk.ciphertext.replace("¢","\n"));
            boolean padded="gost28147".equals(row.algorithm)||"serpent".equals(row.algorithm)||"blowfish".equals(row.algorithm);
            if(text==null || text.length()<chunk.originalUnits || !padded&&text.length()!=chunk.originalUnits)
                throw new IllegalArgumentException("Recorded plaintext chunk length mismatch");
            plain.append(text.substring(0,chunk.originalUnits));
        }
        return plain.toString();
    }
    private static String decryptLegacy(String stored,String algorithm,String material) {
        // Radio RX historically kept one §; Radio TX and current GGWave storage omit it.
        String encoded=stored.endsWith("§")?stored.substring(0,stored.length()-1):stored;
        if(encoded.isEmpty() || !encoded.matches("[A-Za-z0-9+/=¢\\r\\n]+"))
            throw new IllegalArgumentException("unsupported legacy encoding");
        BaseEncryptor cipher=legacyCipher(algorithm,material);
        int block=("gost28147".equals(algorithm)||"blowfish".equals(algorithm))?8:16;
        StringBuilder pending=new StringBuilder(),plain=new StringBuilder();
        int count=0;
        for(String line:encoded.split("[¢\\r\\n]",-1)) {
            if(line.isEmpty()) continue;
            // Legacy Serpent writes just the first 16 bytes and leaves the tail intact.
            // Its encoded byte count need not be a block multiple. A 76-column line
            // could end that chunk OR be a Base64 wrap; no boundary metadata survives.
            if("serpent".equals(algorithm) && line.length()==76 && line.indexOf('=')<0)
                throw new AmbiguousLegacyBoundary();
            pending.append(line);
            String chunk=pending.toString();
            if(chunk.length()%4!=0 || !chunk.matches("[A-Za-z0-9+/]+={0,2}"))
                throw new IllegalArgumentException("malformed legacy base64");
            byte[] bytes=Base64.decode(chunk,Base64.NO_WRAP);
            boolean whole="serpent".equals(algorithm)?bytes.length>=16:bytes.length>0 && bytes.length%block==0;
            if(!whole) {
                // Base64.DEFAULT wraps at 76 columns inside a cipher chunk: 57 decoded
                // bytes cannot end an 8/16-byte block. Retain that line until the chunk ends.
                if(line.length()==76 && line.indexOf('=')<0) continue;
                throw new IllegalArgumentException("malformed legacy cipher block");
            }
            String text=cipher.decrypt(chunk);
            if(text==null || text.isEmpty()) throw new IllegalArgumentException("legacy decryption failed");
            plain.append(text); pending.setLength(0); ++count;
        }
        if(pending.length()!=0 || count==0) throw new IllegalArgumentException("incomplete legacy ciphertext");
        // No trim: GOST/Serpent/Blowfish did not store original lengths. Padding is ambiguous.
        return plain.toString();
    }

    public static final class Session {
        private static final int MAX_UNLOCKS=16, MAX_CHOICES=128;
        private int contactId;
        private long epoch;
        private boolean active;
        private final LinkedHashMap<Integer,Unlocked> unlocked=new LinkedHashMap<>(16,0.75f,true);
        private final LinkedHashMap<Integer,Choice> choices=new LinkedHashMap<>(16,0.75f,true);
        private static final class Unlocked {
            final KeyRef reference; final String material;
            Unlocked(KeyRef reference,String material) { this.reference=reference; this.material=material; }
        }
        private static final class Choice {
            final Row row; String algorithm; Integer keyId;
            Choice(Row row) { this.row=row; }
        }
        public long begin(int intendedContact) {
            if(contactId!=intendedContact) { unlocked.clear(); choices.clear(); }
            contactId=intendedContact; active=intendedContact>0; return ++epoch;
        }
        public long token() { return epoch; }
        public void suspend() { active=false; ++epoch; }
        public void clear() { suspend(); unlocked.clear(); choices.clear(); }
        public boolean current(long token,int intendedContact) { return active && token==epoch && contactId==intendedContact; }
        private static <T> void bound(LinkedHashMap<Integer,T> map,int limit) {
            while(map.size()>limit) map.remove(map.keySet().iterator().next());
        }
        private KeyRef find(List<KeyRef> keys,int id) {
            for(KeyRef key:keys) if(key.id==id && key.contactId==contactId && key.id>0) return key;
            return null;
        }
        public boolean unlock(long token,KeyRef requested,String material,List<KeyRef> liveContactKeys) {
            if(requested==null || !current(token,requested.contactId) || requested.wrapped==null
                    || material==null || material.isEmpty() || material.length()>4096
                    || !requested.same(find(liveContactKeys,requested.id))) return false;
            unlocked.put(requested.id,new Unlocked(requested,material)); bound(unlocked,MAX_UNLOCKS); return true;
        }
        private Choice choice(Row row,boolean create) {
            Choice c=choices.get(row.id);
            if(c!=null && !c.row.same(row)) { choices.remove(row.id); c=null; }
            if(c==null && create) { c=new Choice(row); choices.put(row.id,c); bound(choices,MAX_CHOICES); }
            return c;
        }
        public boolean selectLegacyAlgorithm(long token,Row row,String algorithm) {
            if(!current(token,row.contactId) || row.metadataPresent || !row.encrypted || isModern(row) || !legacyAlgorithmSupported(algorithm)) return false;
            choice(row,true).algorithm=algorithm; return true;
        }
        public boolean selectKey(long token,Row row,KeyRef selected,List<KeyRef> liveContactKeys) {
            if(selected==null || row.metadataPresent || !row.encrypted || !current(token,row.contactId) || selected.contactId!=contactId
                    || !selected.same(find(liveContactKeys,selected.id))) return false;
            choice(row,true).keyId=selected.id; return true;
        }
        public String algorithm(Row row) {
            if(!row.encrypted) return null;
            Description d=describe(row);
            if(d.error!=null) return null;
            if(d.modern!=null) return d.modern.algorithm;
            if(row.metadataPresent)return row.algorithm;
            Choice c=choice(row,false); return c==null?null:c.algorithm;
        }
        public Result display(Row row,List<KeyRef> liveContactKeys) {
            if(!active || row.contactId!=contactId) return failure(Status.STALE_CONTEXT);
            Description d=describe(row);
            if(d.error!=null) return failure(d.error);
            if(!row.encrypted) return new Result(Status.PLAINTEXT,row.stored==null?"":row.stored);
            String algorithm=algorithm(row);
            if(algorithm==null) return failure(Status.MISSING_ALGORITHM);
            Choice c=choice(row,false);
            KeyRef reference=find(liveContactKeys,!row.metadataPresent && c!=null && c.keyId!=null?c.keyId:row.keyId);
            if(reference==null) return failure(row.metadataPresent?Status.MISSING_REFERENCED_KEY:Status.MISSING_KEY);
            Unlocked key=unlocked.get(reference.id);
            if(key==null || !key.reference.same(reference)) return failure(Status.LOCKED_KEY);
            try {
                if(d.modern!=null) return new Result(Status.OPENED,Modem73Crypto.decrypt(d.modern,d.modern.algorithm,key.material));
                if(row.metadataPresent && !row.parameters.isEmpty())return new Result(Status.OPENED,decryptRecordedChunks(row,key.material));
                String text=decryptLegacy(row.stored,algorithm,key.material);
                boolean ambiguous="serpent".equals(algorithm)||"gost28147".equals(algorithm)||"blowfish".equals(algorithm);
                return new Result(ambiguous?Status.OPENED_LEGACY_PADDING:Status.OPENED,text);
            }
            catch(AmbiguousLegacyBoundary ambiguous) { return failure(Status.LEGACY_BOUNDARY_AMBIGUITY); }
            catch(RuntimeException failure) { return failure(Status.DECRYPTION_FAILED); }
        }
    }
}
