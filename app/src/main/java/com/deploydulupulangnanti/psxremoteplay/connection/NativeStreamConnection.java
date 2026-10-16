package com.deploydulupulangnanti.psxremoteplay.connection;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.core.ControllerInput;
import com.deploydulupulangnanti.psxremoteplay.core.ControllerWireMapping;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.core.StreamProfile;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;
import com.deploydulupulangnanti.psxremoteplay.media.MediaCodecVideoDecoder;
import com.deploydulupulangnanti.psxremoteplay.media.PcmAudioSink;

/** Native protocol + bounded media workers. Native handles never travel through an Intent. */
public final class NativeStreamConnection implements AutoCloseable {
    public interface Listener {
        void onConnected();
        void onLoginPin(boolean incorrect);
        void onVideo(long acceptedUnits);
        void onAudioUnavailable();
        void onFailure(String safeMessage);
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService owner = Executors.newSingleThreadExecutor();
    private final ArrayBlockingQueue<byte[]> videoQueue = new ArrayBlockingQueue<>(4);
    private final ArrayBlockingQueue<short[]> audioQueue = new ArrayBlockingQueue<>(12);
    private final AtomicBoolean active = new AtomicBoolean();
    private final Object nativeLock = new Object();
    private final RegistrationStore store;
    private final Listener listener;
    private volatile int sampleRate = 48000, channels = 2;
    private volatile boolean mute;
    private long handle;
    private int systemButtons;
    private ControllerInput.Snapshot lastInput = ControllerInput.Snapshot.neutral();
    private Thread videoThread, audioThread;
    public NativeStreamConnection(RegistrationStore store, Listener listener) { this.store = store; this.listener = listener; }
    public void open(Device device, StreamProfile profile, Surface surface) {
        if (!active.compareAndSet(false, true)) throw new IllegalStateException("Sesi sudah dimulai.");
        mute = !profile.audioEnabled;
        owner.execute(() -> {
            try {
                if (!NativeBridge.available()) { fail("Mesin native tidak tersedia pada perangkat ini."); return; }
                ConsoleRegistration registration = store.load(device.id).orElseThrow(() -> new IllegalStateException("Registrasikan konsol sebelum tersambung."));
                if (!active.get()) return;
                videoThread = new Thread(() -> videoLoop(surface, profile), "remote-video");
                audioThread = new Thread(this::audioLoop, "remote-audio");
                videoThread.start(); audioThread.start();
                long created = NativeBridge.startSession(device.host, registration.ps5, registration.registrationKey(), registration.morning(),
                        profile.height == 1080 ? 1920 : 1280, profile.height, profile.fps, profile.bitrateMbps * 1000,
                        profile.audioEnabled, new NativeBridge.SessionCallback() {
                            @Override public void onConnected() { post(listener::onConnected); }
                            @Override public void onLoginPin(boolean incorrect) { post(() -> listener.onLoginPin(incorrect)); }
                            @Override public void onQuit(int reason) { fail("Sesi konsol berakhir. Periksa jaringan, mode istirahat atau penggunaan akun pada konsol."); }
                            @Override public boolean onVideo(byte[] annexB) {
                                return active.get() && annexB != null && annexB.length > 0 && annexB.length <= 4 * 1024 * 1024 && videoQueue.offer(annexB);
                            }
                            @Override public void onAudioSettings(int rate, int count) { sampleRate = rate; channels = count; }
                            @Override public void onAudio(short[] pcm) {
                                if (active.get() && !mute && pcm != null && pcm.length <= 19200) audioQueue.offer(pcm);
                            }
                        });
                synchronized (nativeLock) {
                    if (active.get()) handle = created;
                    else if (created != 0) NativeBridge.stopSession(created);
                }
                if (created == 0) fail("Sesi tidak dapat dibuka. Periksa pairing dan kemampuan decoder perangkat.");
            } catch (IllegalStateException | IllegalArgumentException | LinkageError error) {
                fail("Koneksi gagal dibuka. Registrasikan ulang konsol atau periksa koneksi lokal.");
            }
        });
    }
    public void input(ControllerInput.Snapshot value) {
        synchronized (nativeLock) {
            lastInput = value;
            if (active.get() && handle != 0) NativeBridge.setController(handle, ControllerWireMapping.buttons(value.buttons) | systemButtons,
                    value.leftX, value.leftY, value.rightX, value.rightY,
                    ControllerWireMapping.trigger(value.leftTrigger, (value.buttons & (1 << 6)) != 0) / 255f,
                    ControllerWireMapping.trigger(value.rightTrigger, (value.buttons & (1 << 7)) != 0) / 255f);
        }
    }
    /** Adds PS / options / touchpad-click buttons to the physical gamepad state. */
    public void systemButtons(int value) {
        synchronized (nativeLock) {
            systemButtons = value & ((1 << 15) | (1 << 14) | (1 << 12));
            input(lastInput);
        }
    }
    public void loginPin(String pin) {
        if (pin == null || !pin.matches("[0-9]{4}")) throw new IllegalArgumentException("PIN login harus 4 digit.");
        synchronized (nativeLock) { if (active.get() && handle != 0) NativeBridge.setLoginPin(handle, pin); }
    }
    private void videoLoop(Surface surface, StreamProfile profile) {
        MediaCodecVideoDecoder decoder = new MediaCodecVideoDecoder();
        long accepted = 0, lastReport = 0;
        try {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", profile.height == 1080 ? 1920 : 1280, profile.height);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4 * 1024 * 1024);
            decoder.configure(format, surface);
            long nextPts = 0;
            while (active.get()) {
                byte[] unit = videoQueue.poll(5, TimeUnit.MILLISECONDS);
                decoder.drain();
                if (unit == null) continue;
                boolean config = configurationOnly(unit);
                long pts = config ? nextPts : (nextPts += 1_000_000L / profile.fps);
                long deadline = SystemClock.elapsedRealtime() + 25;
                while (active.get()) {
                    if (decoder.submit(ByteBuffer.wrap(unit), pts, config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0)) {
                        if (!config) accepted++;
                        break;
                    }
                    if (SystemClock.elapsedRealtime() >= deadline) break;
                    Thread.sleep(2);
                }
                long now = SystemClock.elapsedRealtime();
                if (accepted > 0 && now - lastReport >= 500) {
                    final long count = accepted; post(() -> listener.onVideo(count)); lastReport = now;
                }
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        catch (java.io.IOException | RuntimeException error) { fail("Decoder video gagal. Coba profil 720p/30 fps atau decoder perangkat lain."); }
        finally { decoder.close(); videoQueue.clear(); }
    }
    private static boolean configurationOnly(byte[] data) {
        boolean sawConfig = false;
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == 0 && data[i + 1] == 0 && (data[i + 2] == 1 || (data[i + 2] == 0 && data[i + 3] == 1))) {
                int offset = i + (data[i + 2] == 1 ? 3 : 4);
                if (offset >= data.length) break;
                int type = data[offset] & 31;
                if (type >= 1 && type <= 5) return false;
                sawConfig |= type == 7 || type == 8;
            }
        }
        return sawConfig;
    }
    private void audioLoop() {
        PcmAudioSink sink = new PcmAudioSink();
        int currentRate = 0, currentChannels = 0;
        try {
            while (active.get()) {
                short[] frame = audioQueue.poll(20, TimeUnit.MILLISECONDS);
                if (frame == null || mute) continue;
                int rate = sampleRate, count = channels;
                if (rate != currentRate || count != currentChannels) {
                    sink.configure(rate, count); currentRate = rate; currentChannels = count;
                }
                long deadline = SystemClock.elapsedRealtime() + 40;
                int offset = 0;
                while (active.get() && offset < frame.length && SystemClock.elapsedRealtime() < deadline) {
                    int written = sink.write(frame, offset, frame.length - offset); offset += written;
                    if (written == 0) Thread.sleep(2);
                }
            }
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        catch (RuntimeException error) { mute = true; post(listener::onAudioUnavailable); }
        finally { sink.close(); audioQueue.clear(); }
    }
    private void fail(String message) { post(() -> listener.onFailure(message)); }
    private void post(Runnable event) { main.post(() -> { if (active.get()) event.run(); }); }
    @Override public void close() {
        if (!active.getAndSet(false)) { owner.shutdown(); return; }
        final long old;
        synchronized (nativeLock) { old = handle; handle = 0; }
        owner.execute(() -> {
            try { if (old != 0) NativeBridge.stopSession(old); }
            finally {
                stopThread(videoThread); stopThread(audioThread); videoQueue.clear(); audioQueue.clear();
            }
        });
        owner.shutdown();
    }
    private static void stopThread(Thread thread) {
        if (thread == null) return;
        thread.interrupt();
        try { thread.join(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
