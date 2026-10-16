package com.deploydulupulangnanti.psxremoteplay.platform;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.deploydulupulangnanti.psxremoteplay.core.ControllerInput;

/** Generic Android mapping; no USB driver, pairing, rumble or vendor-specific protocol. */
public final class AndroidController implements InputManager.InputDeviceListener, AutoCloseable {
    public interface Listener {
        void onInput(ControllerInput.Snapshot input);
        void onDevicesChanged(int count);
    }
    private final InputManager manager;
    private final Listener listener;
    private final float deadZone;
    private int buttons;
    private int keyButtons;
    private int hatButtons;
    private int activeDevice = -1;
    private ControllerInput.Snapshot last = ControllerInput.Snapshot.neutral();
    public AndroidController(Context context, float deadZone, Listener listener) {
        manager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        this.deadZone = deadZone; this.listener = listener;
        manager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        refresh();
    }
    public static boolean isController(InputDevice device) {
        return device != null && ((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }
    public boolean motion(MotionEvent event) {
        if (!isController(event.getDevice()) || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        useDevice(event.getDeviceId());
        // D-pad can arrive as either key events or HAT axes.
        hatButtons = 0;
        float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X), hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (hy < -0.5f) hatButtons |= 1 << 12;
        if (hy > 0.5f) hatButtons |= 1 << 13;
        if (hx < -0.5f) hatButtons |= 1 << 14;
        if (hx > 0.5f) hatButtons |= 1 << 15;
        buttons = keyButtons | hatButtons;
        last = new ControllerInput.Snapshot(axis(event, MotionEvent.AXIS_X), axis(event, MotionEvent.AXIS_Y),
                axis(event, MotionEvent.AXIS_Z), axis(event, MotionEvent.AXIS_RZ),
                trigger(event, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE),
                trigger(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS), buttons);
        listener.onInput(last); return true;
    }
    public boolean key(KeyEvent event) {
        if (!isController(event.getDevice())) return false;
        int index = buttonIndex(event.getKeyCode());
        if (index < 0 || (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP)) return false;
        useDevice(event.getDeviceId());
        if (event.getAction() == KeyEvent.ACTION_DOWN) keyButtons |= 1 << index;
        else keyButtons &= ~(1 << index);
        buttons = keyButtons | hatButtons;
        last = new ControllerInput.Snapshot(last.leftX, last.leftY, last.rightX, last.rightY,
                last.leftTrigger, last.rightTrigger, buttons);
        listener.onInput(last); return true;
    }
    private void useDevice(int id) {
        if (activeDevice != id) { reset(); activeDevice = id; }
    }
    private float axis(MotionEvent event, int axis) {
        InputDevice.MotionRange range = event.getDevice().getMotionRange(axis, event.getSource());
        float flat = range == null ? 0 : range.getFlat();
        return ControllerInput.stick(event.getAxisValue(axis), Math.max(deadZone, flat));
    }
    private float trigger(MotionEvent event, int primary, int fallback) {
        int axis = event.getDevice().getMotionRange(primary, event.getSource()) != null ? primary : fallback;
        return ControllerInput.trigger(event.getAxisValue(axis));
    }
    private int buttonIndex(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return 0;
            case KeyEvent.KEYCODE_BUTTON_B: return 1;
            case KeyEvent.KEYCODE_BUTTON_X: return 2;
            case KeyEvent.KEYCODE_BUTTON_Y: return 3;
            case KeyEvent.KEYCODE_BUTTON_L1: return 4;
            case KeyEvent.KEYCODE_BUTTON_R1: return 5;
            case KeyEvent.KEYCODE_BUTTON_L2: return 6;
            case KeyEvent.KEYCODE_BUTTON_R2: return 7;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return 8;
            case KeyEvent.KEYCODE_BUTTON_START: return 9;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 10;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 11;
            case KeyEvent.KEYCODE_DPAD_UP: return 12;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 13;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 14;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 15;
            default: return -1;
        }
    }
    private void refresh() {
        int count = 0;
        for (int id : manager.getInputDeviceIds()) if (isController(manager.getInputDevice(id))) count++;
        listener.onDevicesChanged(count);
    }
    public void reset() { buttons = 0; keyButtons = 0; hatButtons = 0; activeDevice = -1; last = ControllerInput.Snapshot.neutral(); listener.onInput(last); }
    @Override public void onInputDeviceAdded(int id) { refresh(); }
    @Override public void onInputDeviceChanged(int id) { if (activeDevice == id) reset(); refresh(); }
    @Override public void onInputDeviceRemoved(int id) { if (activeDevice == id) reset(); refresh(); }
    @Override public void close() { manager.unregisterInputDeviceListener(this); reset(); }
}
