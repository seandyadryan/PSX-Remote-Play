package com.deploydulupulangnanti.psxremoteplay.core;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Original encoder for the public console discovery wake-up packet. */
public final class WakeupProtocol {
    private WakeupProtocol() { }

    public static byte[] request(ConsoleRegistration registration) {
        Objects.requireNonNull(registration);
        byte[] key = registration.registrationKey();
        int length = 0;
        while (length < key.length && key[length] != 0) length++;
        // Registration stores an ASCII hexadecimal token, not a binary integer or account ID.
        if (length == 0 || length > 8) throw invalidKey();
        long credential = 0;
        for (int i = 0; i < length; i++) {
            int c = key[i] & 255;
            int digit = c >= '0' && c <= '9' ? c - '0'
                    : c >= 'a' && c <= 'f' ? c - 'a' + 10
                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) throw invalidKey();
            credential = (credential << 4) | digit;
        }
        String version = registration.ps5 ? "00030010" : "00020020";
        return ("WAKEUP * HTTP/1.1\nclient-type:vr\nauth-type:R\nmodel:w\napp-type:r\nuser-credential:"
                + Long.toUnsignedString(credential) + "\ndevice-discovery-protocol-version:" + version + "\n\0")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static IllegalArgumentException invalidKey() {
        return new IllegalArgumentException("Kunci registrasi tidak valid untuk wake-up. Daftarkan ulang konsol.");
    }
}
