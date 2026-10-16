package com.deploydulupulangnanti.psxremoteplay.media;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Decoder for the native stream path. All calls use one media worker thread. */
public final class MediaCodecVideoDecoder implements VideoDecoder {
    private MediaCodec codec;
    private String codecName = "none";
    private boolean hardwareAccelerated;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    @Override public void configure(MediaFormat format, Surface surface) throws IOException {
        close();
        if (surface == null || !surface.isValid()) throw new IllegalArgumentException("Surface tidak valid.");
        // Decoder selection must support the entire format, not merely its MIME type.
        String name = new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format);
        if (name == null) throw new IOException("Tidak ada decoder untuk format yang diminta.");
        MediaCodec candidate = MediaCodec.createByCodecName(name);
        try {
            MediaCodecInfo capabilities = candidate.getCodecInfo();
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (Build.VERSION.SDK_INT >= 30 && capabilities.getCapabilitiesForType(mime)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) {
                format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
            }
            candidate.configure(format, surface, null, 0);
            candidate.start();
            codec = candidate; codecName = name;
            hardwareAccelerated = Build.VERSION.SDK_INT >= 29 && capabilities.isHardwareAccelerated();
        } catch (RuntimeException error) { candidate.release(); throw error; }
    }
    public String codecName() { return codecName; }
    /** false on API 26–28 means unknown, not proof of software decoding. */
    public boolean isHardwareAccelerated() { return hardwareAccelerated; }
    @Override public boolean submit(ByteBuffer unit, long ptsUs, int flags) {
        if (codec == null) throw new IllegalStateException("Decoder belum dikonfigurasi.");
        if (unit == null || ptsUs < 0) throw new IllegalArgumentException("Access unit/timestamp tidak valid.");
        drain();
        int index = codec.dequeueInputBuffer(0);
        if (index < 0) return false; // Caller retries or applies a documented drop policy.
        ByteBuffer input = codec.getInputBuffer(index);
        if (input == null || unit.remaining() > input.capacity()) {
            // Return the owned input buffer before rejecting the oversized unit.
            codec.queueInputBuffer(index, 0, 0, ptsUs, 0);
            throw new IllegalArgumentException("Access unit terlalu besar.");
        }
        input.clear(); input.put(unit.duplicate());
        codec.queueInputBuffer(index, 0, unit.remaining(), ptsUs, flags);
        drain(); return true;
    }
    @Override public void drain() {
        if (codec == null) return;
        int index;
        while ((index = codec.dequeueOutputBuffer(info, 0)) >= 0) {
            codec.releaseOutputBuffer(index, info.size > 0);
        }
        // Format-change notification is consumed; surface output is configured by the codec.
    }
    @Override public void close() {
        MediaCodec old = codec; codec = null; codecName = "none"; hardwareAccelerated = false;
        if (old != null) { try { old.stop(); } finally { old.release(); } }
    }
}
