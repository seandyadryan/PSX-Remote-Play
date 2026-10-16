package com.deploydulupulangnanti.psxremoteplay.core;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscoveryProtocolTest {
    private static final String ADVERTISEMENT = "HTTP/1.1 200 OK\n"
            + "host-id:abcdef123456\nhost-name:Living room\nhost-type:PS5\n";

    @Test public void searchUsesCanonicalInteroperableHeaderAndVersions() {
        assertEquals("SRCH * HTTP/1.1\r\ndevice-discovery-protocol-version:00020020\r\n\r\n",
                new String(DiscoveryProtocol.searchRequest(false), StandardCharsets.US_ASCII));
        assertEquals("SRCH * HTTP/1.1\r\ndevice-discovery-protocol-version:00030010\r\n\r\n",
                new String(DiscoveryProtocol.searchRequest(true), StandardCharsets.US_ASCII));
        assertEquals(987, DiscoveryProtocol.PS4_PORT);
        assertEquals(9302, DiscoveryProtocol.PS5_PORT);
    }

    @Test public void responseUsesSenderAddressAndRespectsDatagramSlice() throws Exception {
        byte[] payload = ("ignored" + ADVERTISEMENT + "ignored").getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(payload, 7, ADVERTISEMENT.getBytes(StandardCharsets.UTF_8).length,
                InetAddress.getByName("192.0.2.55"), 9302);
        DiscoveredConsole console = DiscoveryProtocol.parse(packet).orElseThrow(AssertionError::new);
        assertEquals("ABCDEF123456", console.hostId);
        assertEquals("Living room", console.name);
        assertEquals("192.0.2.55", console.host);
        assertEquals("PS5", console.type);
        assertEquals(200, console.statusCode);
        assertEquals("Aktif", console.stateLabel());
    }

    @Test public void acceptsCrLfUtf8RestModeAndTrailingTerminator() throws Exception {
        String text = ADVERTISEMENT.replace("200 OK", "620 Server Standby")
                .replace("Living room", "Ruang keluarga 日本").replace("host-type", "HOST-TYPE")
                .replace("\n", "\r\n") + "\0";
        DiscoveredConsole console = DiscoveryProtocol.parse(packet(text)).orElseThrow(AssertionError::new);
        assertEquals("Ruang keluarga 日本", console.name);
        assertEquals(620, console.statusCode);
        assertEquals("Mode istirahat", console.stateLabel());
    }

    @Test public void defaultsMissingNameAndPreservesUnknownState() throws Exception {
        String text = ADVERTISEMENT.replace("200 OK", "503 Busy").replace("host-name:Living room\n", "");
        DiscoveredConsole console = DiscoveryProtocol.parse(packet(text)).orElseThrow(AssertionError::new);
        assertEquals("PS5", console.name);
        assertEquals("Status tidak diketahui", console.stateLabel());
    }

    @Test public void displayNameFitsAddressBookWithoutSplittingUnicodePairs() throws Exception {
        String longName = "x".repeat(59) + "🎮" + "x".repeat(10);
        DiscoveredConsole shortened = DiscoveryProtocol.parse(packet(ADVERTISEMENT.replace("Living room", longName)))
                .orElseThrow(AssertionError::new);
        assertEquals("x".repeat(59), shortened.name);
        DiscoveredConsole blank = DiscoveryProtocol.parse(packet(ADVERTISEMENT.replace("Living room", "   ")))
                .orElseThrow(AssertionError::new);
        assertEquals("PS5", blank.name);
    }

    @Test public void rejectsMalformedUnboundedAndNonConsolePackets() throws Exception {
        String[] malformed = {
                "", "garbage", ADVERTISEMENT.replace("200 OK", "20 OK"),
                ADVERTISEMENT.replace("HTTP/1.1", "SRCH * HTTP/1.1"),
                ADVERTISEMENT.replace("host-id:abcdef123456\n", ""),
                ADVERTISEMENT.replace("abcdef123456", "bad/id"),
                ADVERTISEMENT.replace("host-type:PS5", "host-type:computer"),
                ADVERTISEMENT + "HOST-ID:conflicting\n", ADVERTISEMENT + "bad header:value\n",
                ADVERTISEMENT.replace("Living room", "bad\rname"),
                ADVERTISEMENT.replace("Living room", "bad\0name"),
                ADVERTISEMENT + "x:" + "a".repeat(4100),
                ADVERTISEMENT + "invalid-line\n"
        };
        for (String text : malformed) assertFalse("Accepted malformed response: " + text, DiscoveryProtocol.parse(packet(text)).isPresent());
        byte[] invalidUtf8 = ADVERTISEMENT.getBytes(StandardCharsets.UTF_8);
        invalidUtf8[invalidUtf8.length - 2] = (byte) 0xFF;
        assertFalse(DiscoveryProtocol.parse(new DatagramPacket(invalidUtf8, invalidUtf8.length,
                InetAddress.getByName("192.0.2.55"), 9302)).isPresent());
        DatagramPacket unspecified = packet(ADVERTISEMENT);
        unspecified.setAddress(InetAddress.getByName("0.0.0.0"));
        assertFalse(DiscoveryProtocol.parse(unspecified).isPresent());
        DatagramPacket ipv6 = packet(ADVERTISEMENT);
        ipv6.setAddress(InetAddress.getByName("::1"));
        assertFalse(DiscoveryProtocol.parse(ipv6).isPresent());
    }

    private DatagramPacket packet(String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(payload, payload.length, InetAddress.getByName("192.0.2.55"), 9302);
    }
}
