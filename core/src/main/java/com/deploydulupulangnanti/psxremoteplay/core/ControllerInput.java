package com.deploydulupulangnanti.psxremoteplay.core;

/** Normalization shared by adapters. Values are finite and bounded. */
public final class ControllerInput {
    private ControllerInput() { }

    public static float stick(float value, float deadZone) {
        if (!Float.isFinite(value) || !Float.isFinite(deadZone) || deadZone < 0 || deadZone >= 1) return 0;
        float magnitude = Math.min(1f, Math.abs(value));
        return magnitude <= deadZone ? 0 : Math.copySign((magnitude - deadZone) / (1 - deadZone), value);
    }

    public static float trigger(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    public static final class Snapshot {
        public final float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger;
        public final int buttons;
        public Snapshot(float lx, float ly, float rx, float ry, float lt, float rt, int buttons) {
            leftX = lx; leftY = ly; rightX = rx; rightY = ry;
            leftTrigger = lt; rightTrigger = rt; this.buttons = buttons;
        }
        public static Snapshot neutral() { return new Snapshot(0, 0, 0, 0, 0, 0, 0); }
    }
}
