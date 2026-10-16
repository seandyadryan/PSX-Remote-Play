package com.deploydulupulangnanti.psxremoteplay.core;

/** Unauthenticated LAN advertisement, not a paired console or proof of identity. */
public final class DiscoveredConsole {
    public final String hostId;
    public final String name;
    public final String host;
    public final String type;
    public final int statusCode;

    DiscoveredConsole(String hostId, String name, String host, String type, int statusCode) {
        this.hostId = hostId;
        this.name = name;
        this.host = host;
        this.type = type;
        this.statusCode = statusCode;
    }

    public String stateLabel() {
        return statusCode == 200 ? "Aktif" : statusCode == 620 ? "Mode istirahat" : "Status tidak diketahui";
    }

    boolean sameAdvertisement(DiscoveredConsole other) {
        return hostId.equals(other.hostId) && name.equals(other.name) && host.equals(other.host)
                && type.equals(other.type) && statusCode == other.statusCode;
    }
}
