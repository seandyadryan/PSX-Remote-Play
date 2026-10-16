package com.deploydulupulangnanti.psxremoteplay.core;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Original bounded codec for the public HTTP-like UDP discovery wire format. */
public final class DiscoveryProtocol {
    public static final int PS4_PORT = 987;
    public static final int PS5_PORT = 9302;
    public static final int MAX_PACKET_BYTES = 4096;

    private DiscoveryProtocol() { }

    public static byte[] searchRequest(boolean ps5) {
        String version = ps5 ? "00030010" : "00020020";
        return ("SRCH * HTTP/1.1\r\ndevice-discovery-protocol-version:" + version + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    public static Optional<DiscoveredConsole> parse(DatagramPacket packet) {
        if (packet == null || !(packet.getAddress() instanceof Inet4Address)
                || packet.getLength() == 0 || packet.getLength() > MAX_PACKET_BYTES
                || packet.getAddress().isAnyLocalAddress() || packet.getAddress().isMulticastAddress()) {
            return Optional.empty();
        }
        try {
            // Some senders include one C terminator. Embedded NUL/control bytes remain invalid.
            int length = packet.getLength();
            if (packet.getData()[packet.getOffset() + length - 1] == 0) length--;
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(packet.getData(), packet.getOffset(), length)).toString();
            if (text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\r' && c != '\n')) return Optional.empty();
            text = text.replace("\r\n", "\n");
            if (text.indexOf('\r') >= 0) return Optional.empty();
            String[] lines = text.split("\n", -1);
            if (lines.length > 65 || !lines[0].matches("HTTP/1\\.[01] [1-9][0-9]{2}(?: [^\\r\\n]*)?")) return Optional.empty();
            int status = Integer.parseInt(lines[0].substring(9, 12));
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line.isEmpty()) continue;
                int colon = line.indexOf(':');
                if (colon <= 0 || line.length() > 512) return Optional.empty();
                String key = line.substring(0, colon).toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (!key.matches("[a-z0-9-]+") || headers.put(key, value) != null) return Optional.empty();
            }
            String id = headers.get("host-id");
            String type = headers.get("host-type");
            if (id == null || !id.matches("[a-zA-Z0-9:_-]{1,80}") || type == null
                    || !(type.equalsIgnoreCase("PS4") || type.equalsIgnoreCase("PS5"))) return Optional.empty();
            type = type.toUpperCase(Locale.ROOT);
            String name = headers.getOrDefault("host-name", type);
            if (name.isEmpty()) name = type;
            // Device stores at most 60 UTF-16 units. Avoid splitting a supplementary character.
            if (name.length() > 60) name = name.substring(0, Character.isHighSurrogate(name.charAt(59)) ? 59 : 60);
            return Optional.of(new DiscoveredConsole(id.toUpperCase(Locale.ROOT), name,
                    packet.getAddress().getHostAddress(), type, status));
        } catch (CharacterCodingException | IllegalArgumentException error) {
            return Optional.empty();
        }
    }
}
