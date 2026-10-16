package com.deploydulupulangnanti.psxremoteplay.platform;

import android.util.Log;

/** Only predefined event/state names; do not pass hosts, names, tokens or raw exceptions. */
public final class AppLog {
    private AppLog() { }
    public static void event(String event) { Log.i("PSXRemotePlay", event); }
}
