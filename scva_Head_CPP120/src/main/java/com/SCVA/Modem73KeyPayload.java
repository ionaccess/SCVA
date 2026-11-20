package com.SCVA;

/** Uses the existing chat key terminator inside a MODEM73 text record. */
public final class Modem73KeyPayload {
    private Modem73KeyPayload() { }

    public static String wire(String wrappedKey) {
        if (wrappedKey == null || wrappedKey.trim().isEmpty()) throw new IllegalArgumentException("Select and unlock a key first");
        return wrappedKey.trim() + "√";
    }

    public static boolean isKey(String text) {
        return text != null && text.length() > 1 && text.endsWith("√");
    }

    public static String display(String text) {
        return isKey(text) ? text.substring(0, text.length() - 1) : text;
    }
}
