package com.scout.control;

/** All fields are guarded by this object, including the serial worker's selections. */
final class SessionState {
    static final int OFF = 0, CONNECTING = 1, ACTIVE = 2, ERROR = 3, WAITING_WIFI = 4;
    int mode, left, right, cap = 35;
    long epoch, lastUi, sequence;
    String token, message = "Ready when you are";
    boolean touching, urgent, stopPending, connectPending, releasePending, releaseWifiPending, leaving;
    float forward, turn;
    synchronized long awaitWifi(long now) {
        clear(); epoch++; lastUi = now; mode = WAITING_WIFI; connectPending = false; releaseWifiPending = false; leaving = false;
        message = "Choose Scout in the Android Wi-Fi prompt"; notifyAll(); return epoch;
    }
    synchronized boolean wifiReady(long requestEpoch, long now) {
        if (epoch != requestEpoch || mode != WAITING_WIFI) return false;
        lastUi = now; mode = CONNECTING; connectPending = true;
        message = "Checking Scout firmware…"; notifyAll(); return true;
    }
    synchronized long connect(long now) {
        clear(); epoch++; lastUi = now; mode = CONNECTING; leaving = false;
        connectPending = true; message = "Checking Scout firmware…"; notifyAll(); return epoch;
    }
    synchronized boolean acceptSession(long requestEpoch, String newToken) {
        if (epoch != requestEpoch || mode != CONNECTING) return false;
        if (newToken == null || !newToken.matches("[0-9a-fA-F]{32}")) return false;
        token = newToken; sequence = 0; mode = ACTIVE; urgent = true;
        message = "Connected · touch to drive"; return true;
    }
    synchronized boolean acceptDrive(long requestEpoch) { return epoch == requestEpoch && mode == ACTIVE; }
    synchronized void input(float f, float t, boolean touch, long now) {
        tick(now);
        if (!active()) return;
        forward = touch ? f : 0; turn = touch ? t : 0; touching = touch; lastUi = now;
        mix(); if (!touch) { urgent = true; releasePending = true; } notifyAll();
    }
    synchronized void speed(int value) { cap = Math.max(0, Math.min(100, value)); mix(); }
    synchronized void heartbeat(long now) { tick(now); lastUi = now; }
    synchronized void tick(long now) {
        if ((mode == ACTIVE || mode == CONNECTING) && now - lastUi > 250) end("Input paused · reconnect to drive");
    }
    synchronized void end(String reason) {
        epoch++; clear(); connectPending = false; mode = OFF; message = reason;
        stopPending = true; notifyAll();
    }
    synchronized void leave(String reason) {
        if (leaving) return;
        leaving = true;
        end(reason); releaseWifiPending = true;
    }
    synchronized void fail(long requestEpoch, String reason) {
        if (epoch != requestEpoch) return;
        end(reason); mode = ERROR;
    }
    private void clear() { forward = turn = 0; left = right = 0; touching = false; token = null; urgent = true; releasePending = false; }
    private void mix() { int[] p = Mixer.mix(forward, turn, cap); left = p[0]; right = p[1]; }
    synchronized boolean active() { return mode == ACTIVE; }
    synchronized int[] takeTargetPair() {
        if (releasePending) { releasePending = false; return new int[] {0, 0}; }
        return new int[] {left, right};
    }
    synchronized boolean takeWifiCleanup(long requestEpoch) {
        if (epoch != requestEpoch || !releaseWifiPending) return false;
        releaseWifiPending = false; return true;
    }
}
