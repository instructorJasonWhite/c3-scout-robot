package com.scout.control;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

final class DriveDatagram {
    static final int PORT = 4210;
    static final String ZERO_TOKEN = "00000000000000000000000000000000";
    static byte[] encode(boolean stop, String token, long sequence, int left, int right) {
        if (token == null || !token.matches("[0-9a-f]{32}") || sequence < 0 || sequence > 0xffffffffL || (!stop && sequence==0) || Math.abs(left)>100 || Math.abs(right)>100)
            throw new IllegalArgumentException("Invalid drive packet");
        return ByteBuffer.allocate(45).put(new byte[]{'S','C','T','2'}).put((byte)(stop?'S':'D')).put(token.getBytes(StandardCharsets.US_ASCII))
            .putInt((int)sequence).putShort((short)left).putShort((short)right).array();
    }
    static final class Ack {
        String token; long sequence, boot; int code, flags, left, right;
    }
    static Ack decode(byte[] bytes, int length) {
        if (length != 50 || bytes[0]!='A'||bytes[1]!='C'||bytes[2]!='K'||bytes[3]!='2') return null;
        ByteBuffer b=ByteBuffer.wrap(bytes,0,length);b.position(4);byte[] token=new byte[32];b.get(token);
        Ack a=new Ack();a.token=new String(token,StandardCharsets.US_ASCII);a.sequence=b.getInt()&0xffffffffL;a.code=b.get()&255;
        a.boot=b.getInt()&0xffffffffL;a.flags=b.get()&255;a.left=b.getShort();a.right=b.getShort();
        return a.code<=3&&a.flags<=7&&Math.abs(a.left)<=100&&Math.abs(a.right)<=100 ? a : null;
    }
}
