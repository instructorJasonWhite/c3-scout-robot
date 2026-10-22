package com.scout.control;

public final class ControlTests {
    private static int checks;
    static void equal(int want, int got, String why) { checks++; if (want != got) throw new AssertionError(why + ": expected " + want + ", got " + got); }
    static void yes(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        int[] out = Mixer.mix(1, 0, 35);
        equal(44, out[0], "35 percent drive maps to useful motor power"); equal(44, out[1], "Forward is symmetric");
        out = Mixer.mix(-1, 0, 35); equal(-44, out[0], "Reverse sign"); equal(-44, out[1], "Reverse symmetry");
        out = Mixer.mix(0, 1, 60); equal(-65, out[0], "Right stick reverses left target for Scout's corrected polarity"); equal(65, out[1], "Right stick advances right target");
        out = Mixer.mix(0, -1, 60); equal(65, out[0], "Left stick advances left target"); equal(-65, out[1], "Left stick reverses right target");
        out = Mixer.mix(1, 1, 35); equal(0, out[0], "Forward right arc inner target stays off"); equal(44, out[1], "Forward right arc uses useful power");
        out = Mixer.mix(1, -1, 35); equal(44, out[0], "Forward left arc uses useful power"); equal(0, out[1], "Forward left arc inner target stays off");
        out = Mixer.mix(-1, 1, 35); equal(0, out[0], "Reverse right arc inner target stays off"); equal(-44, out[1], "Reverse right arc follows steering in reverse");
        out = Mixer.mix(-1, -1, 35); equal(-44, out[0], "Reverse left arc follows steering in reverse"); equal(0, out[1], "Reverse left arc inner target stays off");
        out = Mixer.mix(.03f, .04f, 100); equal(0, out[0], "Radial dead zone"); equal(0, out[1], "Radial dead zone symmetry");
        out = Mixer.mix(.5f, 0, 100); equal(53, out[0], "Analog command uses the useful motor range");
        out = Mixer.mix(1, 0, 0); equal(0, out[0], "Zero cap");
        out = Mixer.mix(1, 0, 30); equal(0, out[0], "30 percent is off");
        out = Mixer.mix(-1, 0, 30); equal(0, out[0], "Negative 30 percent is off");
        out = Mixer.mix(1, 0, 31); equal(40, out[0], "First active percent starts at 40 percent motor power");
        out = Mixer.mix(1, 0, 65); equal(70, out[0], "Middle drive range maps proportionally");
        out = Mixer.mix(1, 0, 100); equal(100, out[0], "Full command stays full power");
        int previous = 0;
        for (int cap = 0; cap <= 100; cap++) {
            int forward = Mixer.mix(1, 0, cap)[0], reverse = Mixer.mix(-1, 0, cap)[0];
            yes(cap > 30 || forward == 0, "Entire low range remains off");
            yes(forward == 0 || (forward >= 40 && forward <= 100), "Never command stalled low motor power");
            yes(reverse == -forward, "Mapping preserves reverse symmetry");
            yes(forward >= previous, "Mapping is monotonic"); previous = forward;
        }
        System.out.println("PASS " + checks + " mixer checks");
    }
}
