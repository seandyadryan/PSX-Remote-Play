package com.deploydulupulangnanti.psxremoteplay.core;

import java.util.Objects;

/** Local address-book entry only; never contains pairing keys or account tokens. */
public final class Device {
    public final String id;
    public final String name;
    public final String host;

    public Device(String id, String name, String host) {
        this.id = requireText(id, "ID", 80);
        this.name = requireText(name, "Nama perangkat", 60);
        this.host = requireText(host, "Host", 253);
        // A host only, never a URL, userinfo, path, port or shell fragment.
        if (!validHost(this.host)) {
            throw new IllegalArgumentException("Gunakan alamat IP atau hostname tanpa URL/port.");
        }
    }

    private static boolean validHost(String value) {
        if (value.contains(":")) {
            // IPv6 literals only, no zone ID, brackets, IPv4 suffix or port notation.
            if (!value.matches("[0-9a-fA-F:]+")) return false;
            String[] halves = value.split("::", -1);
            if (halves.length > 2) return false;
            int count = 0;
            for (String half : halves) {
                if (half.isEmpty()) continue;
                for (String group : half.split(":", -1)) {
                    if (!group.matches("[0-9a-fA-F]{1,4}")) return false;
                    count++;
                }
            }
            return halves.length == 2 ? count < 8 : count == 8;
        }
        if (value.matches("[0-9.]+")) {
            String[] groups = value.split("\\.", -1);
            if (groups.length != 4) return false;
            for (String group : groups) {
                if (!group.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(group) > 255) return false;
            }
            return true;
        }
        for (String label : value.split("\\.", -1)) {
            if (!label.matches("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?")) return false;
        }
        return true;
    }

    public static String requireText(String text, String label, int max) {
        String value = Objects.requireNonNull(text, label).trim();
        if (value.isEmpty() || value.length() > max || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " harus 1–" + max + " karakter tanpa karakter kontrol.");
        }
        return value;
    }
}
