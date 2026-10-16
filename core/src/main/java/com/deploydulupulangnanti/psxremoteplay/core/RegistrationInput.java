package com.deploydulupulangnanti.psxremoteplay.core;

import java.util.Base64;

/** Validates the public inputs before handing registration to the transport. */
public final class RegistrationInput {
    private RegistrationInput() { }

    /** Decimal PSN IDs are serialized as unsigned 64-bit little-endian bytes. */
    public static byte[] accountId(String value) {
        if (value != null && value.matches("[0-9]{1,20}")) {
            java.math.BigInteger number = new java.math.BigInteger(value);
            if (number.bitLength() > 64) throw new IllegalArgumentException("Account ID melebihi 64 bit.");
            byte[] bytes = new byte[8];
            for (int i = 0; i < bytes.length; i++) bytes[i] = number.shiftRight(8 * i).byteValue();
            return bytes;
        }
        return accountIdFromBase64(value);
    }

    /** The eight decoded bytes are already in the order required by the console protocol. */
    public static byte[] accountIdFromBase64(String value) {
        if (value == null || !value.matches("[A-Za-z0-9+/]{11}="))
            throw new IllegalArgumentException("Account ID harus Base64 dari 8 byte (12 karakter dengan =).");
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Account ID Base64 tidak valid."); }
        if (decoded.length != 8 || !Base64.getEncoder().encodeToString(decoded).equals(value))
            throw new IllegalArgumentException("Account ID Base64 tidak valid.");
        return decoded;
    }

    /** The console displays eight decimal digits; leading zeroes remain valid. */
    public static int pin(String value) {
        if (value == null || !value.matches("[0-9]{8}"))
            throw new IllegalArgumentException("PIN konsol harus tepat 8 angka.");
        return Integer.parseInt(value);
    }
}
