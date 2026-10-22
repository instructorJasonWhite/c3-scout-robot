package com.scout.control;

public final class SessionTests {
    static void yes(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static SessionState connected(long now) {
        SessionState s = new SessionState();
        long epoch = s.connect(now);
        yes(s.acceptSession(epoch, "0123456789abcdef0123456789abcdef"), "Connect must accept its neutral session");
        return s;
    }
    public static void main(String[] args) {
        SessionState s = connected(1000);
        yes(s.left == 0 && s.right == 0, "Connect creates zero targets");
        s.input(1, 0, true, 1010); yes(s.left == 44 && s.right == 44, "New touch allows remapped driving");
        s.input(0, 0, false, 1020); yes(s.left == 0 && s.right == 0 && s.urgent, "Release schedules zero promptly");
        s.input(1, 0, true, 1030); long old = s.epoch;
        s.end("Stopped"); yes(!s.acceptSession(old, "0123456789abcdef0123456789abcdef"), "In-flight connect cannot resurrect stopped session");
        yes(!s.acceptDrive(old), "In-flight drive response cannot undo STOP");
        yes(s.left == 0 && s.right == 0 && s.stopPending, "Stop clears input and schedules global stop");
        s.input(1, 0, true, 1050); yes(s.left == 0, "Disconnected input ignored");
        long next = s.connect(1100); s.acceptSession(next, "0123456789abcdef0123456789abcdef");
        yes(s.left == 0 && !s.touching, "Reconnect requires new touch");
        s.input(1, 0, true, 1110); s.tick(1361);
        yes(!s.active() && s.stopPending && s.left == 0, "Frozen UI expires held command");
        s = connected(2000); s.input(1, 0, true, 2010); s.heartbeat(2220); s.tick(2400);
        yes(s.active(), "Responsive UI can hold a stationary touch");
        s.tick(2471); yes(!s.active(), "Heartbeat deadline must not renew on worker ticks");
        s = connected(3000); old = s.epoch; s.fail(old, "Network lost");
        yes(!s.active() && s.stopPending && s.left == 0, "Network failure ends session");
        s.connect(3100); s.fail(old, "Old request failure"); yes(s.mode == SessionState.CONNECTING, "Obsolete failure cannot cancel newer Connect");
        s = connected(4000); s.input(1,0,true,4010); s.speed(10); yes(s.left == 0, "Changing cap below threshold stops held target");
        s.end("Paused"); s.speed(100); yes(s.left == 0, "Speed change cannot restart paused session");
        s = connected(5000); s.input(1,0,true,5010); s.heartbeat(5300);
        yes(!s.active() && s.left == 0, "A UI heartbeat returning after its deadline cannot revive an old touch");
        s = connected(6000); s.input(1,0,true,6010); s.input(1,0,true,6300);
        yes(!s.active() && s.left == 0, "A delayed touch event cannot refresh an expired session");
        s = connected(7000); s.input(1,0,true,7010); s.input(0,0,false,7020); s.input(-1,0,true,7030);
        int[] pair = s.takeTargetPair();
        yes(pair[0] == 0 && pair[1] == 0, "Rapid retouch cannot overwrite release's immediate zero");
        pair = s.takeTargetPair(); yes(pair[0] == -44 && pair[1] == -44, "After release zero the worker samples the latest touch");
        s = new SessionState(); old = s.awaitWifi(8000); s.heartbeat(10000); s.tick(20000);
        yes(s.mode == SessionState.WAITING_WIFI && s.left == 0, "System Wi-Fi prompt may wait without driving or UI expiry");
        yes(s.wifiReady(old, 20001), "Only the current Wi-Fi request may begin connecting");
        yes(s.mode == SessionState.CONNECTING && s.connectPending, "Wi-Fi availability begins a neutral connection");
        s = new SessionState(); old = s.awaitWifi(21000); s.end("Cancelled");
        yes(!s.wifiReady(old, 21001), "Late Wi-Fi callback cannot undo Stop");
        s = connected(22000); s.end("Paused"); s.releaseWifiPending = true; old = s.epoch;
        s.end("Hidden"); s.releaseWifiPending = true;
        yes(!s.takeWifiCleanup(old), "An older in-flight Stop cannot consume newer network cleanup");
        yes(s.releaseWifiPending, "Current Stop still owns the pending cleanup");
        yes(s.takeWifiCleanup(s.epoch) && !s.releaseWifiPending, "Current Stop consumes cleanup exactly once");
        s.releaseWifiPending = true; old = s.epoch; s.awaitWifi(23000);
        yes(!s.takeWifiCleanup(old) && !s.releaseWifiPending, "New explicit Connect supersedes old network cleanup");
        s = new SessionState(); old = s.awaitWifi(24000); s.wifiReady(old,24001);
        s.end("Network replaced during neutral setup"); next = s.awaitWifi(24002);
        yes(!s.acceptSession(old,"0123456789abcdef0123456789abcdef"), "Replaced setup cannot accept an old session reply");
        yes(s.wifiReady(next,24003) && s.left == 0 && s.right == 0, "Network replacement can only retry neutral setup");
        s = connected(25000); s.input(1,0,true,25001); s.leave("Paused"); old = s.epoch;
        yes(s.left == 0 && s.stopPending && s.releaseWifiPending, "Leaving schedules stop and Wi-Fi cleanup");
        s.leave("Hidden");
        yes(s.epoch == old, "Repeated lifecycle exit must preserve the in-flight Stop owner");
        s.stopPending = false; yes(s.takeWifiCleanup(old), "First exit owns network cleanup");
        s.leave("Destroyed");
        yes(!s.stopPending && !s.releaseWifiPending, "Later exit cannot queue Stop after Wi-Fi was released");
        next = s.awaitWifi(26000); s.wifiReady(next,26001); s.acceptSession(next,"0123456789abcdef0123456789abcdef");
        s.leave("Paused again");
        yes(s.stopPending && s.releaseWifiPending, "Explicit reconnect permits a new lifecycle Stop");
        System.out.println("PASS session lifecycle, stale-response, reconnect, release and freshness checks");
    }
}
