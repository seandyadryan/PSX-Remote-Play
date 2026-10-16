package com.deploydulupulangnanti.psxremoteplay.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import com.deploydulupulangnanti.psxremoteplay.core.ControllerInput;

/** Original Canvas animation. Never presented as decoded or received video. */
final class DemoCanvas extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ControllerInput.Snapshot input = ControllerInput.Snapshot.neutral();
    private boolean running;
    DemoCanvas(Context context) { super(context); setContentDescription("Local demo animation with controller stick indicators"); }
    void input(ControllerInput.Snapshot value) { input = value; invalidate(); }
    void running(boolean value) { running = value; if (value) postInvalidateOnAnimation(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(0xFF101F30);
        float width = getWidth(), height = getHeight();
        paint.setColor(0xFF20394B); paint.setStrokeWidth(1);
        for (int x = 0; x < width; x += 48) canvas.drawLine(x, 0, x, height, paint);
        for (int y = 0; y < height; y += 48) canvas.drawLine(0, y, width, y, paint);
        float pulse = (float) Math.sin(SystemClock.uptimeMillis() / 900.0);
        paint.setColor(Ui.ACCENT); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
        canvas.drawCircle(width / 2, height / 2, Math.min(width, height) * (0.20f + pulse * 0.015f), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(width / 2 + input.leftX * width * 0.25f, height / 2 + input.leftY * height * 0.25f, 12, paint);
        paint.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity); paint.setColor(Ui.MUTED);
        canvas.drawText("LOCAL DEMO · CANVAS · NO STREAM", 20, height - 24, paint);
        if (running) postInvalidateOnAnimation();
    }
}
