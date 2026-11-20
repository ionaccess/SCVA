package com.SCVA;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict application wrapper over native modem73's already validated TNC payload. */
public final class Modem73Packet {
    static final int MAX_TNC_BYTES=510, MAX_RECORD_BYTES=2020;
    static final class Admission { final byte[] payload; final String text; Admission(byte[] p,String t){payload=p;text=t;} }
    static final class ParseResult { final String text,reason;private ParseResult(String t,String r){text=t;reason=r;}static ParseResult accepted(String t){return new ParseResult(t,"accepted");}static ParseResult rejected(String r){return new ParseResult(null,r);} }
    public static final class Received {
        public final boolean encrypted;public final String stored,algorithm;public final Modem73Crypto.Encrypted secret;
        private Received(String text){encrypted=false;stored=text;algorithm="";secret=null;}
        private Received(Modem73Crypto.Encrypted record){encrypted=true;stored=record.stored();algorithm=record.algorithm;secret=record;}
    }
    public static Received receive(byte[] bytes){if(Modem73Crypto.claims(bytes))return new Received(Modem73Crypto.parse(bytes));ParseResult plain=parseResult(bytes);if(plain.text==null)throw new IllegalArgumentException("MODEM73 application record rejected: "+plain.reason);return new Received(plain.text);}
    static Admission record(byte[] bytes){Received record=receive(bytes);return new Admission(bytes.clone(),record.stored);}
    private Modem73Packet() { }
    static Admission prepare(String callsign,String text) {
        if(!validCallsign(callsign))throw new IllegalArgumentException("Enter a valid MODEM73 callsign before transmitting");
        if(text==null||text.length()==0)throw new IllegalArgumentException("Message is empty");
        final byte[] body;
        try {ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));body=new byte[encoded.remaining()];encoded.get(body);}
        catch(CharacterCodingException invalid){throw new IllegalArgumentException("Message is not valid UTF-8");}
        byte[] prefix=("M73:"+callsign+":").getBytes(StandardCharsets.US_ASCII);int total=prefix.length+body.length;
        if(total<1||total>MAX_RECORD_BYTES)throw new IllegalArgumentException("MODEM73 allows "+(MAX_RECORD_BYTES-prefix.length)+" UTF-8 message bytes for this callsign");
        byte[] payload=new byte[total];System.arraycopy(prefix,0,payload,0,prefix.length);System.arraycopy(body,0,payload,prefix.length,body.length);return new Admission(payload,text);
    }
    static boolean validCallsign(String value){if(value==null||value.length()<1||value.length()>9)return false;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(!(c=='/'||c>='0'&&c<='9'||c>='A'&&c<='Z'||c>='a'&&c<='z'))return false;}return true;}
    static String parse(byte[] bytes) {
        return parseResult(bytes).text;
    }
    static ParseResult parseResult(byte[] bytes) {
        if (bytes == null || bytes.length < 7 || bytes.length > MAX_RECORD_BYTES) return ParseResult.rejected("invalid_tnc_payload_length");
        String raw;
        try { raw=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException invalid) { return ParseResult.rejected("invalid_utf8"); }
        if (!raw.startsWith("M73:")) return ParseResult.rejected("invalid_chat_prefix");
        int separator=raw.indexOf(':',4);
        if(separator<5 || !validCallsign(raw.substring(4,separator))) return ParseResult.rejected("invalid_callsign_field");
        String text=raw.substring(separator+1);
        if(text.length()==0) return ParseResult.rejected("empty_chat_text");
        for(int i=0;i<text.length();i++) { char c=text.charAt(i); if((c<0x20 && c!='\n' && c!='\r' && c!='\t') || c==0x7f) return ParseResult.rejected("unsupported_control_character"); }
        return ParseResult.accepted(text);
    }
}
