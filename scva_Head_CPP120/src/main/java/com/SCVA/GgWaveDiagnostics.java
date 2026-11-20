package com.SCVA;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/** Bounded, local-only diagnostics for the Phone GGWave path. */
public final class GgWaveDiagnostics
{
	public static final String ENABLED = "GGWAVE_DIAGNOSTICS";
	public static final String CONTENT = "GGWAVE_LOG_CONTENT";
	private static final int MAX_RECORD = 2048, MAX_CONTENT = 512, MAX_QUEUE = 128;
	private static final long ROTATE_BYTES = 256L * 1024L;
	private static final AtomicLong IDS = new AtomicLong(1);
	private static final ArrayDeque<Item> QUEUE = new ArrayDeque<>();
	private static Thread writer;
	private static long dropped;

	private GgWaveDiagnostics()
	{
	}

	public static long nextOperationId()
	{
		return IDS.getAndIncrement();
	}

	public static void event(Context context, String stage, long operationId, String direction, String speed, String fields)
	{
		if (context == null || !enabled(context)) return;
		enqueue(context, new Item(line(stage, operationId, direction, speed, fields), false, false));
	}

	public static void content(Context context, String stage, long operationId, String field, String value)
	{
		if (context == null || !enabled(context) || !contentEnabled(context)) return;
		byte[] original = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
		Escape escaped = escape(value == null ? "" : value, MAX_CONTENT);
		String data = "\"" + safe(field) + "\":\"" + escaped + "\",\"originalBytes\":" + original.length + ",\"truncated\":" + (original.length > MAX_CONTENT || escaped.truncated);
		enqueue(context, new Item(line(stage, operationId, "local", "", data), true, false));
	}

	public static boolean enabled(Context context)
	{
		return prefs(context).getBoolean(ENABLED, false);
	}

	public static boolean contentEnabled(Context context)
	{
		return enabled(context) && prefs(context).getBoolean(CONTENT, false);
	}

	/** Called by settings changes. A pending content record is rechecked by the writer too. */
	public static synchronized void refresh(Context context)
	{
		if (context == null || !contentEnabled(context))
		{
			removeQueuedContent();
		}
		if (context == null || !enabled(context)) QUEUE.clear();
		QUEUE.notifyAll();
	}

	public static synchronized void disableContent()
	{
		removeQueuedContent();
		QUEUE.notifyAll();
	}

	private static void removeQueuedContent()
	{
		Iterator<Item> iterator = QUEUE.iterator();
		while (iterator.hasNext())
			if (iterator.next().content) iterator.remove();
	}

	public static synchronized void disableAll()
	{
		QUEUE.clear();
		dropped = 0;
		QUEUE.notifyAll();
	}

	/** Queue barrier: prior queued records are discarded and deletion runs before later writes. */
	public static synchronized void clear(Context context)
	{
		if (context == null) return;
		QUEUE.clear();
		dropped = 0;
		QUEUE.addLast(new Item("", false, true, context.getApplicationContext()));
		ensureWriter(context.getApplicationContext());
		QUEUE.notifyAll();
	}

	public static String read(Context context)
	{
		StringBuilder out = new StringBuilder();
		if (context == null) return "No GGWave diagnostics";
		File current = file(context, "ggwave.log"), previous = file(context, "ggwave.1.log");
		append(out, previous);
		append(out, current);
		return out.length() == 0 ? "No GGWave diagnostics" : out.toString();
	}

	private static synchronized void enqueue(Context context, Item item)
	{
		Context app = context.getApplicationContext();
		if (item.content && !contentEnabled(app)) return;
		if (QUEUE.size() >= MAX_QUEUE)
		{
			dropped++;
			return;
		}
		QUEUE.addLast(new Item(bound(item.line), item.content, false, app));
		ensureWriter(app);
		QUEUE.notifyAll();
	}

	private static void ensureWriter(Context context)
	{
		if (writer != null && writer.isAlive()) return;
		writer = new Thread(() -> runWriter(context), "SCVA-GGWave-LogWriter");
		writer.setDaemon(true);
		writer.start();
	}

	private static void runWriter(Context context)
	{
		for (;;)
		{
			Item item;
			long lost;
			synchronized (GgWaveDiagnostics.class)
			{
				while (QUEUE.isEmpty())
				{
					try
					{
						GgWaveDiagnostics.class.wait();
					}
					catch (InterruptedException ignored)
					{
						Thread.currentThread().interrupt();
						return;
					}
				}
				item = QUEUE.removeFirst();
				lost = dropped;
				dropped = 0;
			}
			try
			{
				if (item.clear)
				{
					deleteLogs(item.context);
					continue;
				}
				if (!enabled(item.context) || item.content && !contentEnabled(item.context)) continue;
				if (lost > 0) writeLine(item.context, "{\"timeMs\":" + System.currentTimeMillis() + ",\"stage\":\"phone.log.queue\",\"droppedEvents\":" + lost + "}\n");
				writeLine(item.context, bound(item.line));
			}
			catch (Exception ignored)
			{
				synchronized (GgWaveDiagnostics.class)
				{
					dropped += lost + 1;
				}
			}
		}
	}

	private static void writeLine(Context context, String line) throws Exception
	{
		File current = file(context, "ggwave.log"), previous = file(context, "ggwave.1.log");
		byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
		if (current.length() + bytes.length > ROTATE_BYTES)
		{
			if (previous.exists() && !previous.delete()) throw new java.io.IOException("log rotation failed");
			if (current.exists() && !current.renameTo(previous)) throw new java.io.IOException("log rotation failed");
		}
		try (FileOutputStream stream = new FileOutputStream(current, true))
		{
			stream.write(bytes);
		}
	}

	private static void deleteLogs(Context context)
	{
		File current = file(context, "ggwave.log"), previous = file(context, "ggwave.1.log");
		if (current.exists()) current.delete();
		if (previous.exists()) previous.delete();
	}

	private static File file(Context context, String name)
	{
		return new File(context.getFilesDir(), name);
	}

	private static SharedPreferences prefs(Context context)
	{
		return PreferenceManager.getDefaultSharedPreferences(context);
	}

	private static String line(String stage, long id, String direction, String speed, String fields)
	{
		return "{\"timeMs\":" + System.currentTimeMillis() + ",\"stage\":\"" + safe(stage) + "\",\"op\":" + id + ",\"direction\":\"" + safe(direction) + "\",\"speed\":\""
				+ safe(speed) + "\"," + (fields == null || fields.length() == 0 ? "" : fields) + "}\n";
	}

	private static String bound(String value)
	{
		if (value.getBytes(StandardCharsets.UTF_8).length <= MAX_RECORD) return value;
		String marker = "…TRUNCATED\n";
		int limit = MAX_RECORD - marker.getBytes(StandardCharsets.UTF_8).length;
		StringBuilder out = new StringBuilder();
		int used = 0;
		for (int i = 0; i < value.length();)
		{
			int cp = value.codePointAt(i);
			String part = new String(Character.toChars(cp));
			int size = part.getBytes(StandardCharsets.UTF_8).length;
			if (used + size > limit) break;
			out.append(part);
			used += size;
			i += Character.charCount(cp);
		}
		return out.append(marker).toString();
	}

	private static Escape escape(String value, int maxBytes)
	{
		StringBuilder out = new StringBuilder();
		int used = 0;
		for (int i = 0; i < value.length();)
		{
			int cp = value.codePointAt(i);
			i += Character.charCount(cp);
			String part;
			if (cp == '\\') part = "\\\\";
			else if (cp == '"') part = "\\\"";
			else if (cp == '\n') part = "\\n";
			else if (cp == '\r') part = "\\r";
			else if (cp == '\t') part = "\\t";
			else if (cp < 0x20) part = String.format("\\u%04x", cp);
			else
				part = new String(Character.toChars(cp));
			int n = part.getBytes(StandardCharsets.UTF_8).length;
			if (used + n > maxBytes) return new Escape(out.toString(), true);
			out.append(part);
			used += n;
		}
		return new Escape(out.toString(), false);
	}

	private static String safe(String text)
	{
		return text == null ? "" : text.replace("\\", "_").replace("\"", "_").replace("\n", "_").replace("\r", "_");
	}

	private static void append(StringBuilder out, File file)
	{
		if (!file.exists()) return;
		try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream())
		{
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) > 0)
				bytes.write(buffer, 0, n);
			out.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
		}
		catch (Exception ignored)
		{
			out.append("[GGWave log read failed]\n");
		}
	}

	private static final class Item
	{
		final String line;
		final boolean content, clear;
		final Context context;
		Item(String line, boolean content, boolean clear)
		{
			this(line, content, clear, null);
		}

		Item(String line, boolean content, boolean clear, Context context)
		{
			this.line = line;
			this.content = content;
			this.clear = clear;
			this.context = context;
		}
	}
	private static final class Escape
	{
		final String value;
		final boolean truncated;
		Escape(String value, boolean truncated)
		{
			this.value = value;
			this.truncated = truncated;
		}

		@Override
		public String toString()
		{
			return value;
		}
	}
}
