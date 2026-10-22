package com.scout.control;

public final class FaceTests {
    static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        FaceSelection f = new FaceSelection();
        check(!f.queue(1));
        f.initialize(true, true, -1);
        check(f.queue(1)); check(f.queue(8));
        FaceSelection.Ticket t = f.take(7, "token");
        check(t.id == 8 && t.epoch == 7 && t.token.equals("token"));
        check(f.take(7, "token") == null);
        check(!f.current(t, 8, true)); check(!f.current(t, 7, false));
        check(f.queue(-1)); check(!f.current(t, 7, true));
        FaceSelection.Ticket auto = f.take(7, "token");
        check(auto.id == -1 && f.current(auto, 7, true));
        check(!f.queue(10)); check(f.current(auto, 7, true));
        f.clear(); check(!f.current(auto, 7, true)); check(!f.queue(2));
        f.initialize(false, false, -1); check(!f.queue(2));
        f.initialize(true, false, -1); check(!f.queue(2));
        System.out.println("Face selection: coalescing, stale results, lifecycle and bounds passed");
    }
}
