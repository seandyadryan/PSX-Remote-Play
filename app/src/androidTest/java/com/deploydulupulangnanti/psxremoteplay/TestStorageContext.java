package com.deploydulupulangnanti.psxremoteplay;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.util.UUID;

/** Writable target-UID storage with unique preference names and an isolated Keystore alias. */
final class TestStorageContext extends ContextWrapper {
    private final String prefix = "instrumentation_" + UUID.randomUUID().toString().replace("-", "") + "_";
    TestStorageContext(Context target) { super(target); }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return getBaseContext().getSharedPreferences(prefix + name, mode);
    }
    @Override public boolean deleteSharedPreferences(String name) {
        return getBaseContext().deleteSharedPreferences(prefix + name);
    }
    @Override public String getPackageName() { return getBaseContext().getPackageName() + "." + prefix; }
}
