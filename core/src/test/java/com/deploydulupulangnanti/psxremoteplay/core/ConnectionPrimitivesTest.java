package com.deploydulupulangnanti.psxremoteplay.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionPrimitivesTest {
    static ConsoleRegistration registration(boolean ps5, String key) {
        byte[] bytes = new byte[16];
        byte[] ascii = key.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, bytes, 0, ascii.length);
        return new ConsoleRegistration(ps5, "Lab", new byte[6], bytes, new byte[16]);
    }

    @Test public void registrationOwnsEveryCredentialArrayAndDoesNotPrintSecrets() {
        byte[] mac = {1, 2, 3, 4, 5, 6};
        byte[] key = new byte[16];
        byte[] morning = new byte[16];
        key[0] = 97;
        morning[0] = 19;
        ConsoleRegistration value = new ConsoleRegistration(true, "Lab", mac, key, morning);
        Arrays.fill(mac, (byte) 0);
        Arrays.fill(key, (byte) 0);
        Arrays.fill(morning, (byte) 0);
        assertEquals(1, value.serverMac()[0]);
        assertEquals(97, value.registrationKey()[0]);
        assertEquals(19, value.morning()[0]);
        value.serverMac()[0] = 33;
        value.registrationKey()[0] = 33;
        value.morning()[0] = 33;
        assertEquals(1, value.serverMac()[0]);
        assertEquals(97, value.registrationKey()[0]);
        assertEquals(19, value.morning()[0]);
        assertFalse(value.toString().contains("Lab"));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleRegistration(false, "x", new byte[5], new byte[16], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleRegistration(false, "x", new byte[6], new byte[15], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new ConsoleRegistration(false, "x", new byte[6], new byte[16], null));
    }

    @Test public void accountIdPreservesEightDecodedBytesAndPinAcceptsLeadingZeroes() {
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, RegistrationInput.accountIdFromBase64("AQIDBAUGBwg="));
        assertEquals(1234, RegistrationInput.pin("00001234"));
        assertEquals(0, RegistrationInput.pin("00000000"));
        assertEquals(99_999_999, RegistrationInput.pin("99999999"));
        for (String value : new String[]{null, "", "AQIDBAUGBwg", "AQIDBAUGBwg=\n", "AQIDBAUGBwh=", "AAAAAAAAAAA-", "AQIDBAUGBw==", "AQIDBAUGBwgJ"})
            assertThrows(IllegalArgumentException.class, () -> RegistrationInput.accountIdFromBase64(value));
        for (String value : new String[]{null, "1234567", "123456789", "1234 678", "+1234567", "１２３４５６７８", "1234567\n"})
            assertThrows(IllegalArgumentException.class, () -> RegistrationInput.pin(value));
    }

    @Test public void wakeupUsesAsciiHexTokenAsUnsignedDecimalAndCorrectGeneration() {
        byte[] request = WakeupProtocol.request(registration(true, "fFffFFFF"));
        assertEquals("WAKEUP * HTTP/1.1\nclient-type:vr\nauth-type:R\nmodel:w\napp-type:r\nuser-credential:4294967295\ndevice-discovery-protocol-version:00030010\n\0",
                new String(request, StandardCharsets.US_ASCII));
        assertTrue(new String(WakeupProtocol.request(registration(false, "000001f4")), StandardCharsets.US_ASCII)
                .contains("user-credential:500\ndevice-discovery-protocol-version:00020020\n"));
        assertTrue(new String(WakeupProtocol.request(registration(false, "0")), StandardCharsets.US_ASCII)
                .contains("user-credential:0\n"));
        for (String invalid : new String[]{"", "123456789", "abc-", " 123", "0x1234", "\n1234"})
            assertThrows(IllegalArgumentException.class, () -> WakeupProtocol.request(registration(true, invalid)));
    }

    @Test public void eachControllerButtonMapsToItsWirePositionAndTriggersStaySeparate() {
        int[] expectedBits = {0, 1, 2, 3, 8, 9, -1, -1, 13, 12, 10, 11, 6, 7, 4, 5};
        assertEquals(0, ControllerWireMapping.buttons(0));
        for (int i = 0; i < expectedBits.length; i++) {
            int expected = expectedBits[i] == -1 ? 0 : 1 << expectedBits[i];
            assertEquals("Internal button " + i, expected, ControllerWireMapping.buttons(1 << i));
        }
        assertEquals(0x3fff, ControllerWireMapping.buttons(-1));
        assertEquals(0, ControllerWireMapping.buttons(0xffff0000));
        assertEquals(0, ControllerWireMapping.axis(Float.NaN));
        assertEquals(0, ControllerWireMapping.axis(Float.POSITIVE_INFINITY));
        assertEquals(0, ControllerWireMapping.axis(0));
        assertEquals(-32768, ControllerWireMapping.axis(-2));
        assertEquals(32767, ControllerWireMapping.axis(2));
        assertEquals(0, ControllerWireMapping.trigger(Float.NaN, false));
        assertEquals(0, ControllerWireMapping.trigger(-1, false));
        assertEquals(255, ControllerWireMapping.trigger(2, false));
        assertEquals(255, ControllerWireMapping.trigger(0, true));
        assertEquals(128, ControllerWireMapping.trigger(.5f, false));
    }
}
