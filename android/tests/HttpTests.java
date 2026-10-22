package com.scout.control;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class HttpTests {
    static void yes(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static RobotHttp.Reply exchange(String response, int delay) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Throwable[] failure = new Throwable[1];
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(1000);
                    InputStream in = socket.getInputStream(); ByteArrayOutputStream headers = new ByteArrayOutputStream();
                    int c; String h;
                    do { c = in.read(); if (c < 0) throw new EOFException(); headers.write(c); h = headers.toString("US-ASCII"); } while (!h.endsWith("\r\n\r\n"));
                    yes(h.startsWith("POST /drive HTTP/1.1\r\n"), "Correct request line");
                    yes(h.contains("Content-Length: 6\r\n"), "Exact body length");
                    byte[] body = new byte[6]; int count = 0;
                    while (count < 6) { int n = in.read(body, count, 6-count); if (n < 0) throw new EOFException(); count += n; }
                    yes(new String(body, StandardCharsets.US_ASCII).equals("left=0"), "Complete command body arrives");
                    Thread.sleep(delay); socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                } catch (Throwable e) { failure[0] = e; }
            }); peer.start();
            try (Socket socket = new Socket("127.0.0.1", server.getLocalPort())) {
                try { return RobotHttp.exchange(socket, "/drive", "left=0"); }
                finally { peer.join(2000); if (failure[0] != null) throw new AssertionError(failure[0]); }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        RobotHttp.Reply reply = exchange("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}", 300);
        yes(reply.code == 200 && reply.body.equals("{}"), "A delayed reply above the old 200ms limit is read completely");
        reply = exchange("HTTP/1.1 409 Conflict\r\ncontent-length: 4\r\n\r\nstop", 0);
        yes(reply.code == 409 && reply.body.equals("stop"), "Firmware rejection is preserved");
        boolean rejected = false;
        try { exchange("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n{}", 0); } catch (EOFException e) { rejected = true; }
        yes(rejected, "Truncated response rejected");
        rejected = false;
        try { exchange("HTTP/1.1 200 OK\r\nContent-Length: 99999\r\n\r\n", 0); } catch (IOException e) { rejected = true; }
        yes(rejected, "Unbounded response rejected");
        rejected = false;
        try { exchange("HTTP/1.1 200 OK\r\n\r\n{}", 0); } catch (IOException e) { rejected = true; }
        yes(rejected, "Missing length rejected");
        System.out.println("PASS direct HTTP, delayed response, rejection, truncation and bounds checks");
    }
}
