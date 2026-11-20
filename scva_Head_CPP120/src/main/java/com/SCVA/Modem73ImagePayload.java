package com.SCVA;

import android.util.Base64;

/** Recognizes bounded Base64 images supported by Android's bitmap renderer. */
public final class Modem73ImagePayload {
    private Modem73ImagePayload() { }

    public static boolean isImage(String text) {
        return isImage(text, Modem73Packet.MAX_RECORD_BYTES);
    }

    public static boolean isImage(String text, int maximumLength) {
        if (text == null || text.length() < 8 || text.length() > maximumLength) return false;
        try {
            byte[] bytes = Base64.decode(text, Base64.DEFAULT);
            boolean jpeg = bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                    && (bytes[bytes.length - 2] & 255) == 255 && (bytes[bytes.length - 1] & 255) == 217;
            boolean webp = bytes.length >= 16 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P'
                    && ((bytes[4] & 255) | (bytes[5] & 255) << 8 | (bytes[6] & 255) << 16 | (bytes[7] & 255) << 24) == bytes.length - 8;
            return jpeg || webp;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }
}
