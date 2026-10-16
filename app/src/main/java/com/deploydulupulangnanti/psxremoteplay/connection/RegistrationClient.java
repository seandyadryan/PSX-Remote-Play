package com.deploydulupulangnanti.psxremoteplay.connection;

import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;

/** Owns a native registration attempt and joins it away from the UI thread. */
public final class RegistrationClient implements AutoCloseable {
    public interface Listener { void onSuccess(ConsoleRegistration registration); void onFailure(String safeMessage); }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private final RegistrationStore store;
    private final String deviceId;
    private volatile boolean closed;
    private long handle;
    public RegistrationClient(RegistrationStore store, String deviceId) { this.store = store; this.deviceId = deviceId; }
    public void start(String host, boolean ps5, byte[] accountId, int pin, Listener listener) {
        int attempt = generation.incrementAndGet();
        byte[] account = accountId.clone();
        execute(() -> {
            release();
            if (!valid(attempt)) { Arrays.fill(account, (byte) 0); return; }
            if (!NativeBridge.available()) { fail(attempt, listener, "Mesin koneksi tidak tersedia pada ABI perangkat ini."); return; }
            try {
                handle = NativeBridge.register(host, ps5, account, pin, new NativeBridge.RegistrationCallback() {
                    @Override public void onSuccess(boolean resultPs5, String name, byte[] mac, byte[] key, byte[] morning) {
                        execute(() -> {
                            if (!valid(attempt)) return;
                            try {
                                ConsoleRegistration registration = new ConsoleRegistration(resultPs5, name, mac, key, morning);
                                store.save(deviceId, registration);
                                main.post(() -> { if (valid(attempt)) listener.onSuccess(registration); });
                            } catch (IllegalStateException | IllegalArgumentException error) {
                                fail(attempt, listener, "Pairing diterima, tetapi hasil tidak dapat disimpan. Coba registrasi ulang.");
                            } finally { release(); }
                        });
                    }
                    @Override public void onFailure() {
                        fail(attempt, listener, "Registrasi gagal. Periksa IP, tipe konsol, ID akun dan PIN yang belum kedaluwarsa.");
                        execute(() -> { if (valid(attempt)) release(); });
                    }
                });
                if (handle == 0) fail(attempt, listener, "Registrasi tidak dapat dimulai. Periksa jaringan dan coba lagi.");
            } catch (RuntimeException | LinkageError error) {
                release(); fail(attempt, listener, "Mesin registrasi gagal dibuka. Periksa hasil build atau coba lagi.");
            } finally { Arrays.fill(account, (byte) 0); }
        });
    }
    private boolean valid(int attempt) { return !closed && generation.get() == attempt; }
    private void fail(int attempt, Listener listener, String message) {
        main.post(() -> { if (valid(attempt)) listener.onFailure(message); });
    }
    private void execute(Runnable action) { try { worker.execute(action); } catch (RejectedExecutionException ignored) { } }
    private void release() { if (handle != 0) { long old = handle; handle = 0; NativeBridge.stopRegistration(old); } }
    @Override public void close() {
        closed = true; generation.incrementAndGet(); execute(this::release); worker.shutdown();
    }
}
