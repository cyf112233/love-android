package org.love2d.android.engineoverlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Touch controls drawn with a plain View over the SDL surface. */
final class TouchView extends View {
    private static Method keyDownMethod;
    private static Method keyUpMethod;

    static {
        try {
            Class<?> sdl = Class.forName("org.libsdl.app.SDLActivity");
            keyDownMethod = findMethod(sdl, "onNativeKeyDown");
            keyUpMethod = findMethod(sdl, "onNativeKeyUp");
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name, int.class);
        } catch (Throwable ignored) {
        }
        try {
            Method method = type.getDeclaredMethod(name, int.class);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private final EngineOverlay overlay;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Config config;
    private boolean editing;
    private String selectedId;

    private final Map<Integer, Config.Control> pressed = new HashMap<>();
    private int joystickPointer = -1;
    private Config.Control joystickControl;
    private final Set<Integer> joystickKeys = new HashSet<>();
    private float knobX;
    private float knobY;

    private int dragPointer = -1;
    private Config.Control dragControl;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean dragMoved;
    private float downX;
    private float downY;
    private Config.Control pendingDoubleControl;
    private String lastTapId;
    private long lastTapAt;
    private float lastTapX;
    private float lastTapY;

    TouchView(Context context, EngineOverlay overlay) {
        super(context);
        this.overlay = overlay;
        this.density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setFocusable(false);
        setFocusableInTouchMode(false);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setPathEffect(new DashPathEffect(
            new float[]{10f * density, 8f * density}, 0f));
        selectedPaint.setStyle(Paint.Style.STROKE);
    }

    void setConfig(Config config) {
        this.config = config;
        selectedId = null;
        invalidate();
    }

    void setEditing(boolean editing) {
        if (this.editing == editing) return;
        this.editing = editing;
        if (!editing) {
            releaseAll();
            selectedId = null;
        }
        invalidate();
    }

    Config.Control selectedControl() {
        if (config == null || selectedId == null) return null;
        for (Config.Control control : config.controls) {
            if (selectedId.equals(control.id)) return control;
        }
        return null;
    }

    void select(String id) {
        selectedId = id;
        invalidate();
    }

    void releaseAll() {
        for (Config.Control control : pressed.values()) {
            sendKey(control.key, false);
        }
        pressed.clear();
        releaseJoystick();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (config == null) return;
        if (!config.enabled && !editing) return;

        for (Config.Control control : config.controls) {
            drawControl(canvas, control);
        }
    }

    private void drawControl(Canvas canvas, Config.Control control) {
        float cx = control.x * getWidth();
        float cy = control.y * getHeight();
        float radius = control.sizeDp * density / 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(control.color);
        paint.setAlpha((int) (clamp(control.alpha, 0.05f, 1f) * 255));
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        paint.setColor(Color.WHITE);
        paint.setAlpha(110);
        canvas.drawCircle(cx, cy, radius, paint);

        if (control.isJoystick()) {
            drawJoystick(canvas, control, cx, cy, radius);
        } else {
            textPaint.setColor(isLight(control.color) ? Color.BLACK : Color.WHITE);
            textPaint.setAlpha(230);
            textPaint.setTextSize(radius * 0.82f);
            String label = control.label == null ? "" : control.label;
            if (label.length() > 3) label = label.substring(0, 3);
            float baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(label, cx, baseline, textPaint);
        }

        if (editing) {
            boolean selected = control.id.equals(selectedId);
            if (selected) {
                selectedPaint.setStrokeWidth(3f * density);
                selectedPaint.setColor(Color.rgb(80, 170, 255));
                selectedPaint.setAlpha(255);
                canvas.drawCircle(cx, cy, radius + 6f * density, selectedPaint);
            } else {
                dashedPaint.setStrokeWidth(1.5f * density);
                dashedPaint.setColor(Color.WHITE);
                dashedPaint.setAlpha(140);
                canvas.drawCircle(cx, cy, radius + 6f * density, dashedPaint);
            }
        }
    }

    private void drawJoystick(Canvas canvas, Config.Control control,
                              float cx, float cy, float radius) {
        float knobRadius = radius * 0.42f;
        boolean active = joystickControl != null && joystickControl.id.equals(control.id);
        float kx = active ? knobX : cx;
        float ky = active ? knobY : cy;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (clamp(control.alpha, 0.05f, 1f) * 200));
        canvas.drawCircle(cx, cy, radius * 0.70f, paint);

        paint.setColor(control.color);
        paint.setAlpha((int) (clamp(control.alpha, 0.05f, 1f) * 255));
        canvas.drawCircle(kx, ky, knobRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        paint.setColor(Color.WHITE);
        paint.setAlpha(190);
        canvas.drawCircle(kx, ky, knobRadius, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (config == null) return false;
        if (!config.enabled && !editing) return false;
        if (editing) return handleEditTouch(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return handlePointerDown(event, 0);
            case MotionEvent.ACTION_POINTER_DOWN:
                return handlePointerDown(event, event.getActionIndex());
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                handlePointerUp(event.getPointerId(event.getActionIndex()));
                return true;
            case MotionEvent.ACTION_UP:
                handlePointerUp(event.getPointerId(event.getActionIndex()));
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                return true;
            default:
                return false;
        }
    }

    private boolean handleEditTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Config.Control control = findControl(event.getX(), event.getY());
                dragMoved = false;
                pendingDoubleControl = null;
                if (control != null) {
                    long now = SystemClock.uptimeMillis();
                    boolean isDouble = control.id.equals(lastTapId)
                        && now - lastTapAt >= 0
                        && now - lastTapAt <= 450
                        && Math.abs(event.getX() - lastTapX) <= 32f * density
                        && Math.abs(event.getY() - lastTapY) <= 32f * density;

                    selectedId = control.id;
                    if (isDouble) {
                        // Open the property dialog after the second tap is
                        // released, not while the finger is still down.
                        pendingDoubleControl = control;
                        lastTapId = null;
                        lastTapAt = 0L;
                        dragControl = null;
                        dragPointer = -1;
                    } else {
                        dragPointer = event.getPointerId(0);
                        dragControl = control;
                        dragOffsetX = event.getX() - control.x * getWidth();
                        dragOffsetY = event.getY() - control.y * getHeight();
                        downX = event.getX();
                        downY = event.getY();
                    }
                    invalidate();
                } else {
                    selectedId = null;
                    dragControl = null;
                    pendingDoubleControl = null;
                    lastTapId = null;
                    lastTapAt = 0L;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                Config.Control control = dragControl;
                if (control == null) return true;
                int index = event.findPointerIndex(dragPointer);
                if (index < 0) return true;
                if (!dragMoved) {
                    // A normal tap with a little finger jitter must stay a tap,
                    // otherwise the second tap is never recognised as double.
                    if (Math.hypot(event.getX(index) - downX, event.getY(index) - downY)
                        <= 8f * density) {
                        return true;
                    }
                    dragMoved = true;
                }
                float nx = (event.getX(index) - dragOffsetX) / getWidth();
                float ny = (event.getY(index) - dragOffsetY) / getHeight();
                control.x = clamp(nx, 0.04f, 0.96f);
                control.y = clamp(ny, 0.04f, 0.96f);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                Config.Control control = dragControl;
                Config.Control pending = pendingDoubleControl;
                pendingDoubleControl = null;
                if (pending != null && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
                    overlay.showPropertyDialog(pending);
                } else if (dragMoved && control != null) {
                    overlay.saveConfig();
                } else if (control != null) {
                    lastTapId = control.id;
                    lastTapAt = SystemClock.uptimeMillis();
                    lastTapX = event.getX();
                    lastTapY = event.getY();
                }
                dragControl = null;
                dragPointer = -1;
                dragMoved = false;
                return true;
            }
            default:
                return true;
        }
    }

    private boolean handlePointerDown(MotionEvent event, int index) {
        Config.Control control = findControl(event.getX(index), event.getY(index));
        if (control == null) return false;
        int pointerId = event.getPointerId(index);

        if (control.isJoystick()) {
            if (joystickPointer != -1) return true;
            joystickPointer = pointerId;
            joystickControl = control;
            updateJoystick(control, event.getX(index), event.getY(index));
        } else {
            pressed.put(pointerId, control);
            sendKey(control.key, true);
        }
        invalidate();
        return true;
    }

    private void handleMove(MotionEvent event) {
        Config.Control control = joystickControl;
        if (control == null) return;
        int index = event.findPointerIndex(joystickPointer);
        if (index >= 0) {
            updateJoystick(control, event.getX(index), event.getY(index));
        }
    }

    private void handlePointerUp(int pointerId) {
        if (pointerId == joystickPointer) {
            releaseJoystick();
        }
        Config.Control control = pressed.remove(pointerId);
        if (control != null) sendKey(control.key, false);
        invalidate();
    }

    private void updateJoystick(Config.Control control, float x, float y) {
        float cx = control.x * getWidth();
        float cy = control.y * getHeight();
        float radius = control.sizeDp * density / 2f;
        float dx = x - cx;
        float dy = y - cy;
        float distance = (float) Math.hypot(dx, dy);
        if (distance > radius) {
            dx = dx / distance * radius;
            dy = dy / distance * radius;
        }
        knobX = cx + dx;
        knobY = cy + dy;

        Set<Integer> newKeys = new HashSet<>();
        if (distance > radius * 0.22f) {
            double angle = Math.toDegrees(Math.atan2(-dy, dx));
            if (angle >= -22.5 && angle < 22.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT);
            } else if (angle >= 22.5 && angle < 67.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_UP);
                newKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT);
            } else if (angle >= 67.5 && angle < 112.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_UP);
            } else if (angle >= 112.5 && angle < 157.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_UP);
                newKeys.add(KeyEvent.KEYCODE_DPAD_LEFT);
            } else if (angle >= 157.5 || angle < -157.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_LEFT);
            } else if (angle >= -157.5 && angle < -112.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_DOWN);
                newKeys.add(KeyEvent.KEYCODE_DPAD_LEFT);
            } else if (angle >= -112.5 && angle < -67.5) {
                newKeys.add(KeyEvent.KEYCODE_DPAD_DOWN);
            } else {
                newKeys.add(KeyEvent.KEYCODE_DPAD_DOWN);
                newKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT);
            }
        }

        for (Integer key : joystickKeys) {
            if (!newKeys.contains(key)) sendKey(key, false);
        }
        for (Integer key : newKeys) {
            if (!joystickKeys.contains(key)) sendKey(key, true);
        }
        joystickKeys.clear();
        joystickKeys.addAll(newKeys);
        invalidate();
    }

    private void releaseJoystick() {
        for (Integer key : joystickKeys) sendKey(key, false);
        joystickKeys.clear();
        joystickPointer = -1;
        joystickControl = null;
        invalidate();
    }

    private Config.Control findControl(float x, float y) {
        if (config == null) return null;
        float slop = 12f * density;
        for (int i = config.controls.size() - 1; i >= 0; i--) {
            Config.Control control = config.controls.get(i);
            float cx = control.x * getWidth();
            float cy = control.y * getHeight();
            float radius = control.sizeDp * density / 2f + slop;
            if (Math.abs(x - cx) <= radius && Math.abs(y - cy) <= radius
                && Math.hypot(x - cx, y - cy) <= radius) {
                return control;
            }
        }
        return null;
    }

    static void sendKey(int keyCode, boolean down) {
        if (keyCode == 0) return;
        Method method = down ? keyDownMethod : keyUpMethod;
        if (method == null) return;
        try {
            method.invoke(null, keyCode);
        } catch (Throwable ignored) {
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150;
    }
}
