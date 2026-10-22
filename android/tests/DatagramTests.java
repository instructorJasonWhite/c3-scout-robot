package com.scout.control;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
public final class DatagramTests {
    static void yes(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args){
        String token="0123456789abcdef0123456789abcdef";
        byte[] p=DriveDatagram.encode(false,token,0xffffffffL,-44,100);
        yes(p.length==45&&p[3]=='2'&&p[4]=='D',"Drive framing");
        ByteBuffer b=ByteBuffer.wrap(p);yes((b.getInt(37)&0xffffffffL)==0xffffffffL&&b.getShort(41)==-44&&b.getShort(43)==100,"Signed targets and unsigned sequence");
        p=DriveDatagram.encode(true,DriveDatagram.ZERO_TOKEN,0,0,0);yes(p[4]=='S',"Stop requires no live session");
        byte[] ack=ByteBuffer.allocate(50).put(new byte[]{'A','C','K','2'}).put(token.getBytes(StandardCharsets.US_ASCII)).putInt(7).put((byte)0).putInt(0xfedcba98).put((byte)3).putShort((short)-44).putShort((short)100).array();
        DriveDatagram.Ack a=DriveDatagram.decode(ack,50);
        yes(a!=null&&a.token.equals(token)&&a.sequence==7&&a.boot==0xfedcba98L&&a.left==-44&&a.right==100,"Ack identity and targets");
        yes(DriveDatagram.decode(ack,49)==null,"Truncated ack ignored");ack[3]='1';yes(DriveDatagram.decode(ack,50)==null,"Old protocol ignored");
        boolean rejected=false;try{DriveDatagram.encode(false,token,1,101,0);}catch(IllegalArgumentException e){rejected=true;}
        yes(rejected,"Invalid target rejected");
        System.out.println("PASS packet framing, session identity, sequence, signs and rejection checks");
    }
}
