package com.deploydulupulangnanti.psxremoteplay.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValidationTest {
    @Test public void stickDeadZoneIsContinuousAndBounded() {
        assertEquals(0, ControllerInput.stick(0.1f, 0.12f), 0);
        assertEquals(-1, ControllerInput.stick(-2, 0.12f), 0);
        assertEquals(0.5f, ControllerInput.stick(0.56f, 0.12f), 0.0001f);
        assertEquals(0, ControllerInput.stick(Float.NaN, 0.12f), 0);
        assertEquals(1, ControllerInput.trigger(5), 0);
        assertEquals(0, ControllerInput.trigger(-1), 0);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUrlInsteadOfHost() {
        new Device("id", "Test", "https://user:secret@example.com/path");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidBitrate() {
        new StreamProfile("p", "Test", 720, 60, 0, true, 0.1f);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidDeadZone() {
        new StreamProfile("p", "Test", 720, 60, 12, true, Float.NaN);
    }
    @Test public void acceptsLiteralAddressesAndHostnamesWithoutDnsLookups() {
        for (String host : new String[]{"192.168.1.10", "console.local", "localhost", "::1", "2001:db8::1234"}) {
            assertEquals(host, new Device("d", "Lab", host).host);
        }
    }
    @Test public void rejectsInvalidAddressesAndPorts() {
        for (String host : new String[]{"host:9295", "999.1.2.3", ":::1", "a..b", "-host", "host/secret", "1.2.3.4:12"}) {
            try { new Device("d", "Lab", host); fail("Accepted invalid host: " + host); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
