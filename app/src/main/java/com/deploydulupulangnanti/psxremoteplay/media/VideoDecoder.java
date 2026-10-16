package com.deploydulupulangnanti.psxremoteplay.media;

import android.media.MediaFormat;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Worker-thread contract. Complete access units with monotonic presentation timestamps. */
public interface VideoDecoder extends AutoCloseable {
    void configure(MediaFormat format, Surface surface) throws IOException;
    boolean submit(ByteBuffer accessUnit, long presentationTimeUs, int flags);
    void drain();
    @Override void close();
}
