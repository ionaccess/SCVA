package com.SCVA;

import android.util.Base64;
import com.SCVA.encryption.*;
import java.nio.*;
import java.nio.charset.*;
import java.io.ByteArrayOutputStream;
import java.util.*;

/** MODEM73 application envelope. Cipher implementations and their parameters remain unchanged. */
public final class Modem73Crypto {
    private static final byte[] MAGIC={'S','C','M','7','3','E'};
    public static final String STORED_PREFIX="SCM73E1:";
    private static final String[] ALGORITHMS={"", "aes", "rc6", "twofish", "", "gost28147", ""};
    public static String keyContext(int localId,String wrapped){
        if(localId<=0||wrapped==null)return "missing";
        try{return localId+":"+Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(wrapped.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}catch(java.security.NoSuchAlgorithmException unavailable){throw error("local key context unavailable");}
    }
    public static boolean supports(String algorithm){return algorithm!=null&&!algorithm.isEmpty()&&id(algorithm)>0;}
    private static int id(String a){for(int i=1;i<ALGORITHMS.length;i++)if(!ALGORITHMS[i].isEmpty()&&ALGORITHMS[i].equals(a))return i;return -1;}
    public static boolean claims(byte[] bytes){if(bytes==null||bytes.length<MAGIC.length)return false;for(int i=0;i<MAGIC.length;i++)if(bytes[i]!=MAGIC[i])return false;return true;}
    public static final class Encrypted {
        public final String algorithm,callsign;private final String[] chunks;private final int[] units;private final byte[] wire;
        private Encrypted(String a,String c,String[] p,int[] u,byte[] w){algorithm=a;callsign=c;chunks=p;units=u;wire=w.clone();}
        public String stored(){return STORED_PREFIX+Base64.encodeToString(wire,Base64.NO_WRAP);}
        public byte[] bytes(){return wire.clone();}
    }
    private static IllegalArgumentException error(String reason){return new IllegalArgumentException("MODEM73 encryption: "+reason);}
    private static byte[] utf8(String text){try{ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));byte[] out=new byte[b.remaining()];b.get(out);return out;}catch(CharacterCodingException e){throw error("invalid UTF-8 input");}}
    private static String text(byte[] bytes){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){throw error("invalid encoded UTF-8");}}
    private static BaseEncryptor cipher(String algorithm,String key){
        if(!supports(algorithm))throw error("unsupported algorithm (Serpent requires separate review)");
        if(key==null||key.isEmpty())throw error("unlock this contact's default key first");
        int n=utf8(key).length;
        if((algorithm.equals("aes")||algorithm.equals("twofish"))&&n!=16&&n!=24&&n!=32)throw error("key must contain 16, 24 or 32 UTF-8 bytes for this algorithm");
        if(algorithm.equals("rc6")&&(key.length()<16||!key.matches("[A-Za-z0-9]+")))throw error("RC6 requires at least 16 ASCII letters/digits with the existing key mapping");
        if(algorithm.equals("gost28147")&&n!=32)throw error("GOST requires a 32-byte key");
        switch(algorithm){case "aes":return new Aes256Encryptor(key);case "rc6":return new Rc6Encryptor(key);case "twofish":return new TwofishEncryptor(key);case "gost28147":return new Gost28147Encryptor(key);default:throw error("unsupported algorithm");}
    }
    private static void encoded(String value){String raw=value.replace("¢","");if(value.isEmpty()||!value.endsWith("¢")||raw.length()%4!=0||!raw.matches("[A-Za-z0-9+/]+={0,2}"))throw error("invalid ciphertext encoding");}
    private static String decryptChunk(BaseEncryptor cipher,String chunk,int units){String out;try{out=cipher.decrypt(chunk.replace("¢","\n"));}catch(RuntimeException e){throw error("decryption failed");}if(out==null||out.length()<units)throw error("decryption failed");if(!(cipher instanceof Gost28147Encryptor)&&out.length()!=units)throw error("decrypted chunk length mismatch");String trimmed=out.substring(0,units);utf8(trimmed);return trimmed;}
    public static byte[] prepare(String callsign,String algorithm,String key,String plain){
        if(!Modem73Packet.validCallsign(callsign))throw error("invalid callsign");
        if(plain==null||plain.isEmpty())throw error("message is empty");utf8(plain);
        BaseEncryptor cipher=cipher(algorithm,key);List<byte[]> chunks=new ArrayList<>();List<Integer> units=new ArrayList<>();
        final int size;try{size=cipher.getEncryptorChunkSize();}catch(RuntimeException e){throw error("cipher/provider initialization failed");}if(size<1||size>256)throw error("cipher/provider initialization failed");
        int total=0;
        for(int i=0;i<plain.length();){int end=Math.min(plain.length(),i+size);if(end<plain.length()&&Character.isHighSurrogate(plain.charAt(end-1))&&Character.isLowSurrogate(plain.charAt(end)))end--;if(end<=i)throw error("unsupported character boundary");String part=plain.substring(i,end), encrypted;
            try{encrypted=cipher.encrypt(part);}catch(RuntimeException failure){throw error("cipher/provider preparation failed");}
            if(encrypted==null)throw error("cipher/provider preparation failed");encrypted=encrypted.replace("\n","¢");encoded(encrypted);
            if(!part.equals(decryptChunk(cipher,encrypted,part.length())))throw error("existing cipher cannot reconstruct this input");
            byte[] bytes=utf8(encrypted);chunks.add(bytes);units.add(part.length());total+=bytes.length;i=end;
            if(chunks.size()>128||14+callsign.length()+4*chunks.size()+total>2020)throw error("final encrypted record exceeds 2020 bytes including envelope/ciphertext overhead");
        }
        ByteBuffer wire=ByteBuffer.allocate(14+callsign.length()+4*chunks.size()+total).order(ByteOrder.BIG_ENDIAN);wire.put(MAGIC).put((byte)1).put((byte)1).put((byte)id(algorithm)).put((byte)callsign.length()).putShort((short)chunks.size()).putShort((short)total).put(callsign.getBytes(StandardCharsets.US_ASCII));
        for(int i=0;i<chunks.size();i++)wire.putShort((short)(int)units.get(i)).putShort((short)chunks.get(i).length);
        for(byte[] chunk:chunks)wire.put(chunk);return wire.array();
    }
    public static Encrypted parse(byte[] bytes){
        if(!claims(bytes)||bytes.length<19||bytes.length>2020)throw error("invalid encrypted record length/magic");ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);b.position(6);
        if(b.get()!=1||b.get()!=1)throw error("unsupported version/type");int algorithm=b.get()&255,c=b.get()&255,k=b.getShort()&65535,l=b.getShort()&65535;
        if(algorithm<1||algorithm>=ALGORITHMS.length||!supports(ALGORITHMS[algorithm]))throw error("unsupported algorithm");
        if(c<1||c>9||k<1||k>128||l<1||14+c+4*k+l!=bytes.length)throw error("invalid encrypted record dimensions");byte[] call=new byte[c];b.get(call);String callsign=new String(call,StandardCharsets.US_ASCII);if(!Modem73Packet.validCallsign(callsign))throw error("invalid sender metadata");
        int[] units=new int[k],lengths=new int[k];int sum=0;for(int i=0;i<k;i++){units[i]=b.getShort()&65535;lengths[i]=b.getShort()&65535;if(units[i]<1||units[i]>256||lengths[i]<1)throw error("invalid chunk dimensions");sum+=lengths[i];}if(sum!=l)throw error("ciphertext length mismatch");String[] chunks=new String[k];for(int i=0;i<k;i++){byte[] chunk=new byte[lengths[i]];b.get(chunk);chunks[i]=text(chunk);encoded(chunks[i]);int decoded=Base64.decode(chunks[i].replace("¢","\n"),Base64.DEFAULT).length;if(decoded==0||decoded%(algorithm==5?8:16)!=0)throw error("ciphertext block length mismatch");}return new Encrypted(ALGORITHMS[algorithm],callsign,chunks,units,bytes);
    }
    public static Encrypted stored(String stored){if(stored==null||!stored.startsWith(STORED_PREFIX)||stored.length()>2800)throw error("invalid encrypted storage record");try{String body=stored.substring(STORED_PREFIX.length());if(!body.matches("[A-Za-z0-9+/]+={0,2}"))throw error("invalid encrypted storage encoding");return parse(Base64.decode(body,Base64.NO_WRAP));}catch(RuntimeException e){throw error("invalid encrypted storage record");}}
    public static String decrypt(Encrypted record,String configuredAlgorithm,String key){if(record==null||!record.algorithm.equals(configuredAlgorithm))throw error("configured algorithm does not match encrypted record");BaseEncryptor cipher=cipher(configuredAlgorithm,key);StringBuilder plain=new StringBuilder();for(int i=0;i<record.chunks.length;i++)plain.append(decryptChunk(cipher,record.chunks[i],record.units[i]));return plain.toString();}
}
