package com.scout.control;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Small, bounded HTTP exchange for the fixed local robot; no proxy or connection pool. */
final class RobotHttp {
    static final class Reply {
        final int code; final String body;
        Reply(int code, String body) { this.code = code; this.body = body; }
    }
    static Reply exchange(Socket socket, String path, String body) throws IOException {
        socket.setTcpNoDelay(true);
        String payload = body == null ? "" : body;
        byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
        String request = (body == null ? "GET " : "POST ") + path + " HTTP/1.1\r\nHost: 192.168.4.1\r\nAccept: application/json\r\nConnection: close\r\n";
        if (body != null) request += "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + bytes.length + "\r\n";
        // Header and body fit in one write, avoiding tiny separate TCP writes.
        socket.getOutputStream().write((request + "\r\n" + payload).getBytes(StandardCharsets.US_ASCII));
        long deadline = System.nanoTime() + 600_000_000L;
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        int[] remainingHeaders = {4096};
        String status = line(socket, input, deadline, remainingHeaders);
        String[] parts = status.split(" ", 3); int code;
        try { if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) throw new NumberFormatException(); code = Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) { throw new IOException("Invalid Scout response", e); }
        int length = -1;
        while (true) {
            String header = line(socket, input, deadline, remainingHeaders);
            if (header.isEmpty()) break;
            int colon = header.indexOf(':'); if (colon < 1) throw new IOException("Invalid Scout header");
            String name = header.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = header.substring(colon + 1).trim();
            if (name.equals("content-length")) {
                if (length != -1) throw new IOException("Duplicate Scout response length");
                try { length = Integer.parseInt(value); } catch (NumberFormatException e) { throw new IOException("Invalid Scout response length", e); }
                if (length < 0 || length > 8192) throw new IOException("Scout response too large");
            }
            if (name.equals("transfer-encoding")) throw new IOException("Unsupported Scout response");
        }
        if (length < 0) throw new IOException("Missing Scout response length");
        byte[] response = new byte[length]; int count = 0;
        while (count < length) {
            timeout(socket, deadline); int n = input.read(response, count, length - count);
            if (n < 0) throw new EOFException("Truncated Scout response"); count += n;
        }
        return new Reply(code, new String(response, StandardCharsets.UTF_8));
    }
    private static String line(Socket socket, InputStream input, long deadline, int[] remaining) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); int previous = -1;
        while (remaining[0]-- > 0) {
            timeout(socket, deadline); int c = input.read(); if (c < 0) throw new EOFException("Truncated Scout headers");
            if (previous == '\r' && c == '\n') {
                byte[] bytes = out.toByteArray(); return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            out.write(c); previous = c;
        }
        throw new IOException("Scout headers too large");
    }
    private static void timeout(Socket socket, long deadline) throws IOException {
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        if (remaining <= 0) throw new SocketTimeoutException("Scout reply timed out");
        socket.setSoTimeout((int) Math.min(600, remaining));
    }
}
