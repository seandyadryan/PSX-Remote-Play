package com.deploydulupulangnanti.psxremoteplay.core;

/** Converts this app's Android controller layout to the public Chiaki transport layout. */
public final class ControllerWireMapping {
    private ControllerWireMapping() { }

    public static int buttons(int internalMask) {
        int result = internalMask & 0x000f; // A/B/X/Y -> cross/circle/square/triangle
        result |= (internalMask & 0x0030) << 4; // L1/R1
        result |= (internalMask & 0x0100) << 5; // Select -> share
        result |= (internalMask & 0x0200) << 3; // Start -> options
        result |= internalMask & 0x0c00; // L3/R3
        result |= (internalMask & 0x3000) >>> 6; // up/down
        result |= (internalMask & 0xc000) >>> 10; // left/right
        return result; // L2/R2 travel separately as analog bytes.
    }

    public static short axis(float value) {
        if (!Float.isFinite(value)) return 0;
        float bounded = Math.max(-1, Math.min(1, value));
        return (short) Math.round(bounded * (bounded < 0 ? 32768f : 32767f));
    }

    public static int trigger(float value, boolean digitalPressed) {
        return digitalPressed ? 255 : Math.round(ControllerInput.trigger(value) * 255f);
    }
}
