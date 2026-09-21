package com.SCVA;

import java.util.ArrayDeque;

/** Binary application handoff. Bounded, process-local; never converts to text. */
public final class V2Inbox {
    private static final ArrayDeque<byte[]> messages = new ArrayDeque<byte[]>();
    private V2Inbox() {}
    public static synchronized boolean offer(byte[] data) {
        if (data == null || data.length == 0 || data.length > 4096 || messages.size() == 4) return false;
        messages.addLast(data.clone());
        return true;
    }
    public static synchronized byte[] poll() { return messages.pollFirst(); }
    public static synchronized int size() { return messages.size(); }
}
