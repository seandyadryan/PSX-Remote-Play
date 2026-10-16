package com.deploydulupulangnanti.psxremoteplay.core;

/** Credentials returned by a successful console registration. Never log this object or its bytes. */
public final class ConsoleRegistration {
    public final boolean ps5;
    public final String name;
    private final byte[] serverMac;
    private final byte[] registrationKey;
    private final byte[] morning;

    public ConsoleRegistration(boolean ps5, String name, byte[] serverMac, byte[] registrationKey, byte[] morning) {
        this.ps5 = ps5;
        this.name = Device.requireText(name, "Nama konsol", 128);
        this.serverMac = copy(serverMac, 6);
        this.registrationKey = copy(registrationKey, 16);
        this.morning = copy(morning, 16);
    }

    private static byte[] copy(byte[] value, int size) {
        if (value == null || value.length != size) throw new IllegalArgumentException("Ukuran kredensial konsol tidak valid.");
        return value.clone();
    }

    public byte[] serverMac() { return serverMac.clone(); }
    public byte[] registrationKey() { return registrationKey.clone(); }
    public byte[] morning() { return morning.clone(); }
}
