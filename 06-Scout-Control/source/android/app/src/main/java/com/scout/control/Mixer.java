package com.scout.control;

public final class Mixer {
    private Mixer() { }
    public static int[] mix(float forward, float turn, int cap) {
        if (!Float.isFinite(forward) || !Float.isFinite(turn)) return new int[] {0, 0};
        float radius = (float) Math.hypot(forward, turn);
        if (radius <= .08f) return new int[] {0, 0};
        float gain = (Math.min(1, radius) - .08f) / .92f / radius;
        // Scout's motor polarity is calibrated on the ESP; steering uses the
        // opposite differential so left/right match the assembled rover.
        // Reverse steering with travel direction; keep stationary turns unchanged.
        float steering = forward < 0 ? -turn : turn;
        float left = (forward - steering) * gain;
        float right = (forward + steering) * gain;
        float scale = Math.max(1, Math.max(Math.abs(left), Math.abs(right)));
        int limit = Math.max(0, Math.min(100, cap));
        return new int[] {motorPower(Math.round(left / scale * limit)), motorPower(Math.round(right / scale * limit))};
    }
    private static int motorPower(int command) {
        int magnitude = Math.abs(command);
        if (magnitude <= 30) return 0;
        int power = 40 + (Math.min(100, magnitude) - 30) * 60 / 70;
        return command < 0 ? -power : power;
    }
}
