package org.love2d.android.engineoverlay;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Larger, Miuix-like switch for the injected engine-pack menu. */
final class ToggleView extends View {
    interface OnCheckedChangeListener {
        void onCheckedChanged(ToggleView view, boolean checked);
    }

    private static final int TRACK_OFF = 0x4DFFFFFF;
    private static final int TRACK_ON = Color.rgb(80, 170, 255);
    private static final int THUMB = Color.WHITE;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final float density;
    private boolean checked;
    private float position;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    ToggleView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
    }

    void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean checked) {
        setChecked(checked, false);
    }

    private void setChecked(boolean checked, boolean fromUser) {
        if (this.checked == checked) return;
        this.checked = checked;
        animateTo(checked ? 1f : 0f);
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    private void animateTo(float target) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(position, target);
        animator.setDuration(150L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                position = (float) animation.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = (int) (72 * density);
        int height = (int) (40 * density);
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float padding = 4f * density;
        float height = getHeight() - padding * 2f;
        float radius = height / 2f;
        track.set(padding, padding, getWidth() - padding, getHeight() - padding);

        int trackColor = blend(TRACK_OFF, TRACK_ON, position);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(trackColor);
        canvas.drawRoundRect(track, radius, radius, paint);

        float thumbRadius = radius - 3f * density;
        float thumbX = padding + radius + position * (track.width() - radius * 2f);
        float thumbY = padding + radius;
        paint.setColor(THUMB);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            setChecked(!checked, true);
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private static int blend(int from, int to, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.argb(a, r, g, b);
    }
}
