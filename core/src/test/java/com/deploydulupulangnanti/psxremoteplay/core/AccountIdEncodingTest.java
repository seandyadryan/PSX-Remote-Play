package com.deploydulupulangnanti.psxremoteplay.core;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountIdEncodingTest {
    @Test public void decimalAndBase64SelectSameEightLittleEndianBytes() {
        assertArrayEquals(new byte[]{8,7,6,5,4,3,2,1}, RegistrationInput.accountId("72623859790382856"));
        assertArrayEquals(RegistrationInput.accountId("72623859790382856"), RegistrationInput.accountId("CAcGBQQDAgE="));
    }
    @Test public void unsignedMaximumAndZeroAreRepresentableButOverflowIsRejected() {
        byte[] maximum = new byte[8]; Arrays.fill(maximum, (byte) 255);
        assertArrayEquals(maximum, RegistrationInput.accountId("18446744073709551615"));
        assertArrayEquals(new byte[8], RegistrationInput.accountId("0"));
        for (String value : new String[]{"18446744073709551616", "-1", "+1", "1.0", ""}) {
            try { RegistrationInput.accountId(value); fail("Invalid account ID was accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
