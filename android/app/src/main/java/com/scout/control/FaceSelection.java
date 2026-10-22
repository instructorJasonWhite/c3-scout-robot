package com.scout.control;

/** Latest-only face requests. Caller holds ScoutClient.state's monitor. */
final class FaceSelection {
    static final String[] NAMES = {"Happy", "Laughing", "Surprised", "Angry", "Sad",
        "Sleepy", "Skeptical", "Winking", "Cheeky", "Dizzy"};
    static final class Ticket {
        final long epoch, generation; final String token; final int id;
        Ticket(long epoch, long generation, String token, int id) {
            this.epoch = epoch; this.generation = generation; this.token = token; this.id = id;
        }
    }
    boolean supported, available, pending;
    int selected = -1;
    long generation;
    String message = "Connect to choose a face";
    void initialize(boolean supported, boolean available, int selection) {
        ++generation; pending = false;
        this.supported = supported; this.available = available;
        selected = selection >= -1 && selection < NAMES.length ? selection : -1;
        message = !supported ? "Firmware update needed for face controls" :
            !available ? "OLED unavailable · check wiring and restart Scout" : label(selected);
    }
    static String label(int id) { return id == -1 ? "Auto · all ten expressions" : NAMES[id]; }
    boolean queue(int id) {
        if (!supported || !available || id < -1 || id >= NAMES.length) return false;
        selected = id; pending = true; ++generation;
        message = "Selecting " + label(id) + "…";
        return true;
    }
    Ticket take(long epoch, String token) {
        if (!pending) return null;
        pending = false;
        return new Ticket(epoch, generation, token, selected);
    }
    boolean current(Ticket ticket, long epoch, boolean active) {
        return active && ticket.epoch == epoch && ticket.generation == generation;
    }
    void clear() {
        ++generation; pending = false; supported = available = false;
        message = "Connect to choose a face";
    }
}
