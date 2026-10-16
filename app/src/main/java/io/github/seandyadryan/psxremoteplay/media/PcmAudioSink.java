package io.github.seandyadryan.psxremoteplay.media;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Prototype PCM16 sink. Caller owns audio focus and decoding, and must use a worker thread. */
public final class PcmAudioSink implements AutoCloseable {
    private AudioTrack track;
    private int channels;
    public void configure(int sampleRate, int channelCount) {
        close();
        if ((sampleRate != 44100 && sampleRate != 48000) || (channelCount != 1 && channelCount != 2)) {
            throw new IllegalArgumentException("PCM harus 44,1/48 kHz, mono/stereo.");
        }
        int mask = channelCount == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int minimum = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IllegalStateException("Format audio tidak didukung.");
        AudioTrack candidate = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(mask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum * 2).build();
        try {
            if (candidate.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack gagal dibuat.");
            candidate.play(); track = candidate; channels = channelCount;
        } catch (RuntimeException error) { candidate.release(); throw error; }
    }
    /** Returns samples consumed; caller retains any unwritten tail and applies bounded buffering. */
    public int write(short[] pcm, int offset, int count) {
        if (track == null) throw new IllegalStateException("Audio belum dikonfigurasi.");
        if (pcm == null || offset < 0 || count < 0 || offset > pcm.length - count || count % channels != 0) {
            throw new IllegalArgumentException("Buffer PCM tidak valid.");
        }
        int written = track.write(pcm, offset, count, AudioTrack.WRITE_NON_BLOCKING);
        if (written < 0) throw new IllegalStateException("AudioTrack gagal menulis: " + written);
        return written;
    }
    @Override public void close() {
        AudioTrack old = track; track = null;
        if (old != null) { try { old.pause(); old.flush(); } finally { old.release(); } }
    }
}
