package com.scout.control;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class JoystickView extends View {
    interface Listener { void move(float forward, float turn, boolean touching); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;
    private int pointer = -1;
    private float x, y;
    private boolean driving;
    private final int mint = 0xffa3e9c0;
    JoystickView(Context context, Listener listener) {
        super(context); this.listener = listener;
        setContentDescription("Drive joystick. Drag up to go forward, down to reverse, left or right to turn. Release to stop.");
        setFocusable(true); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void ready(boolean value) { driving = value; if (!value) reset(); invalidate(); }
    void reset() { pointer = -1; x = y = 0; invalidate(); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.max(dp(20), Math.min(getWidth(), getHeight()) / 2f - dp(25));
        paint.setShader(new RadialGradient(cx, cy, radius, new int[] {0xff223c30, 0xff13251d}, null, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx, cy, radius, paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0xff355044);
        canvas.drawCircle(cx, cy, radius, paint); paint.setColor(0xff294236);
        canvas.drawCircle(cx, cy, radius * .68f, paint); canvas.drawCircle(cx, cy, radius * .34f, paint);
        paint.setColor(0xff365347);
        canvas.drawLine(cx - radius + dp(12), cy, cx + radius - dp(12), cy, paint);
        canvas.drawLine(cx, cy - radius + dp(12), cx, cy + radius - dp(12), paint);
        paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(10));
        paint.setColor(driving ? 0xffb4d1c1 : 0xff708d7d);
        canvas.drawText("FORWARD", cx, cy - radius - dp(10), paint);
        canvas.drawText("REVERSE", cx, cy + radius + dp(18), paint);
        float travel = radius * .68f;
        float kx = cx + x * travel, ky = cy + y * travel;
        paint.setColor(0x55000000); canvas.drawCircle(kx, ky + dp(5), radius * .23f, paint);
        paint.setColor(driving ? mint : 0xff4f6d5c); canvas.drawCircle(kx, ky, radius * .23f, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(driving ? 0xffd7f7e5 : 0xff789583);
        canvas.drawCircle(kx, ky, radius * .23f - dp(4), paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xff183a28);
        Path arrow = new Path(); float a = radius * .075f;
        arrow.moveTo(kx, ky - a); arrow.lineTo(kx + a, ky + a); arrow.lineTo(kx, ky + a * .45f); arrow.lineTo(kx - a, ky + a); arrow.close();
        canvas.drawPath(arrow, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!driving) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(0); getParent().requestDisallowInterceptTouchEvent(true); sample(event, 0); return true;
        }
        if (action == MotionEvent.ACTION_MOVE && pointer != -1) {
            int index = event.findPointerIndex(pointer); if (index < 0) release(); else sample(event, index); return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer)) {
            release(); if (action == MotionEvent.ACTION_UP) performClick(); return true;
        }
        return true;
    }
    private void sample(MotionEvent event, int index) {
        float radius = Math.max(dp(20), Math.min(getWidth(), getHeight()) / 2f - dp(25)) * .68f;
        x = (event.getX(index) - getWidth() / 2f) / radius;
        y = (event.getY(index) - getHeight() / 2f) / radius;
        float length = (float) Math.hypot(x, y); if (length > 1) { x /= length; y /= length; }
        listener.move(-y, x, true); invalidate();
    }
    private void release() { reset(); getParent().requestDisallowInterceptTouchEvent(false); listener.move(0, 0, false); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
