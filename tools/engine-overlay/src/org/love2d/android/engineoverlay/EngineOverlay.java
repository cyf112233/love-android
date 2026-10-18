package org.love2d.android.engineoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight overlay injected into the prebuilt LÖVE 11.5 / 0.10.2 engine
 * packs. It is a plain-View sibling of the SDL surface, not a Compose UI, so
 * it stays cheap and works without adding Compose to those APKs.
 */
final class EngineOverlay extends FrameLayout {
    private static final String TAG = "engine_touch_overlay";
    private static final int BG = 0xF21C1C1E;
    private static final int TEXT = Color.WHITE;
    private static final int SUBTEXT = 0xB3FFFFFF;
    private static final int ACCENT = Color.rgb(80, 170, 255);
    private static final int DANGER = Color.rgb(255, 96, 96);

    private final Activity host;
    private final SharedPreferences preferences;
    private Config config;
    private final TouchView touchView;
    private final BallView ball;
    private final LinearLayout hintBar;
    private final float density;

    private EngineOverlay(Activity activity) {
        super(activity);
        host = activity;
        density = getResources().getDisplayMetrics().density;
        preferences = activity.getSharedPreferences("engine_touch", Context.MODE_PRIVATE);
        config = Config.load(preferences);

        setWillNotDraw(true);
        setClickable(false);

        touchView = new TouchView(activity, this);
        addView(touchView, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        ball = new BallView(activity, this);
        addView(ball, new LayoutParams(dp(48), dp(48)));

        hintBar = buildHintBar();
        addView(hintBar, new LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT) {{
            gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            topMargin = dp(12);
        }});

        applyConfig();
        post(new Runnable() {
            @Override
            public void run() {
                positionBall();
            }
        });
    }

    static void install(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        if (content.findViewWithTag(TAG) != null) {
            resume(activity);
            return;
        }
        EngineOverlay overlay = new EngineOverlay(activity);
        overlay.setTag(TAG);
        content.addView(overlay, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    }

    static void resume(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        View view = content.findViewWithTag(TAG);
        if (view instanceof EngineOverlay) {
            ((EngineOverlay) view).onHostResume();
        }
    }

    static void pause(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        View view = content.findViewWithTag(TAG);
        if (view instanceof EngineOverlay) {
            ((EngineOverlay) view).touchView.releaseAll();
        }
    }

    private void onHostResume() {
        config = Config.load(preferences);
        applyConfig();
        post(new Runnable() {
            @Override
            public void run() {
                positionBall();
            }
        });
    }

    private void applyConfig() {
        touchView.setConfig(config);
        touchView.setEditing(config.editing);
        hintBar.setVisibility(config.editing ? View.VISIBLE : View.GONE);
        positionBall();
    }

    void saveConfig() {
        config.save(preferences);
    }

    private void positionBall() {
        if (getWidth() == 0 || getHeight() == 0 || ball.getWidth() == 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    positionBall();
                }
            });
            return;
        }
        float maxX = Math.max(0, getWidth() - ball.getWidth());
        float maxY = Math.max(0, getHeight() - ball.getHeight());
        ball.setX(Math.max(0f, Math.min(maxX, config.ballX * getWidth() - ball.getWidth() / 2f)));
        ball.setY(Math.max(0f, Math.min(maxY, config.ballY * getHeight() - ball.getHeight() / 2f)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        positionBall();
    }

    private void showMenu() {
        touchView.releaseAll();

        final Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            params.gravity = Gravity.BOTTOM;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        LinearLayout card = card();
        card.addView(titleRow("游戏菜单", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
            }
        }));

        ToggleView toggle = new ToggleView(host);
        toggle.setChecked(config.enabled);
        toggle.setOnCheckedChangeListener(new ToggleView.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ToggleView view, boolean isChecked) {
                config.enabled = isChecked;
                if (!isChecked) config.editing = false;
                saveConfig();
                applyConfig();
            }
        });
        card.addView(switchRow("启用触控键位", "在游戏画面上显示摇杆和按键", toggle));

        card.addView(divider());

        card.addView(row("编辑键位", "拖动、添加、删除；双击键位修改属性", new Runnable() {
            @Override
            public void run() {
                config.enabled = true;
                config.editing = true;
                saveConfig();
                applyConfig();
                dialog.dismiss();
            }
        }));

        card.addView(divider());

        card.addView(rowDanger("退出游戏", "关闭当前游戏", new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
                confirmExit();
            }
        }));

        FrameLayout wrapper = new FrameLayout(host);
        wrapper.setPadding(dp(12), 0, dp(12), dp(12));
        wrapper.addView(card, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrapper);
        dialog.show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(host)
            .setTitle("退出游戏？")
            .setMessage("未保存的游戏进度可能会丢失。")
            .setNegativeButton("取消", null)
            .setPositiveButton("退出", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    host.finish();
                }
            })
            .show();
    }

    void showPropertyDialog(final Config.Control control) {
        final Dialog dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            params.gravity = Gravity.CENTER;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        LinearLayout card = card();
        card.addView(titleRow(
            control.isJoystick() ? "摇杆" : "按钮 " + control.label,
            new Runnable() {
                @Override
                public void run() {
                    dialog.dismiss();
                }
            }));

        final TextView sizeValue = valueText(control.sizeDp + "dp");
        SeekBar sizeBar = new SeekBar(host);
        sizeBar.setMax(208);
        sizeBar.setProgress(Math.max(0, Math.min(208, control.sizeDp - 32)));
        sizeBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                control.sizeDp = 32 + progress;
                sizeValue.setText(control.sizeDp + "dp");
                touchView.invalidate();
                saveConfig();
            }
        });
        card.addView(sliderRow("大小", sizeBar, sizeValue));

        final TextView alphaValue = valueText((int) (control.alpha * 100) + "%");
        SeekBar alphaBar = new SeekBar(host);
        alphaBar.setMax(90);
        alphaBar.setProgress(Math.max(0, Math.min(90, (int) (control.alpha * 100) - 10)));
        alphaBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                control.alpha = (10 + progress) / 100f;
                alphaValue.setText((10 + progress) + "%");
                touchView.invalidate();
                saveConfig();
            }
        });
        card.addView(sliderRow("透明度", alphaBar, alphaValue));

        card.addView(colorRow(control));

        if (!control.isJoystick()) {
            card.addView(row("触发键：" + control.label, "点击选择实际触发的按键", new Runnable() {
                @Override
                public void run() {
                    showKeyPicker(control, dialog);
                }
            }));
        }

        LinearLayout actions = new LinearLayout(host);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(action("删除", DANGER, new Runnable() {
            @Override
            public void run() {
                config.controls.remove(control);
                saveConfig();
                touchView.select(null);
                applyConfig();
                dialog.dismiss();
            }
        }));
        actions.addView(action("关闭", TEXT, new Runnable() {
            @Override
            public void run() {
                dialog.dismiss();
            }
        }));
        card.addView(actions);

        ScrollView scroll = new ScrollView(host);
        scroll.addView(card, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout wrapper = new FrameLayout(host);
        wrapper.setPadding(dp(12), dp(12), dp(12), dp(12));
        wrapper.addView(scroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrapper);
        dialog.show();
    }

    private void showKeyPicker(final Config.Control control, final Dialog parent) {
        final List<String> labels = new ArrayList<>();
        final List<Integer> codes = new ArrayList<>();
        addKey(labels, codes, "ESC", KeyEvent.KEYCODE_ESCAPE);
        addKey(labels, codes, "Z", KeyEvent.KEYCODE_Z);
        addKey(labels, codes, "X", KeyEvent.KEYCODE_X);
        addKey(labels, codes, "C", KeyEvent.KEYCODE_C);
        addKey(labels, codes, "A", KeyEvent.KEYCODE_A);
        addKey(labels, codes, "S", KeyEvent.KEYCODE_S);
        addKey(labels, codes, "D", KeyEvent.KEYCODE_D);
        addKey(labels, codes, "W", KeyEvent.KEYCODE_W);
        addKey(labels, codes, "Q", KeyEvent.KEYCODE_Q);
        addKey(labels, codes, "E", KeyEvent.KEYCODE_E);
        addKey(labels, codes, "R", KeyEvent.KEYCODE_R);
        addKey(labels, codes, "F", KeyEvent.KEYCODE_F);
        addKey(labels, codes, "SPACE", KeyEvent.KEYCODE_SPACE);
        addKey(labels, codes, "ENTER", KeyEvent.KEYCODE_ENTER);
        addKey(labels, codes, "SHIFT", KeyEvent.KEYCODE_SHIFT_LEFT);
        addKey(labels, codes, "TAB", KeyEvent.KEYCODE_TAB);
        addKey(labels, codes, "↑", KeyEvent.KEYCODE_DPAD_UP);
        addKey(labels, codes, "↓", KeyEvent.KEYCODE_DPAD_DOWN);
        addKey(labels, codes, "←", KeyEvent.KEYCODE_DPAD_LEFT);
        addKey(labels, codes, "→", KeyEvent.KEYCODE_DPAD_RIGHT);

        String[] items = labels.toArray(new String[0]);
        new AlertDialog.Builder(host)
            .setTitle("触发键")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    control.key = codes.get(which);
                    control.label = labels.get(which);
                    touchView.invalidate();
                    saveConfig();
                    parent.dismiss();
                    showPropertyDialog(control);
                }
            })
            .show();
    }

    private void showAddDialog() {
        new AlertDialog.Builder(host)
            .setTitle("添加控件")
            .setItems(new String[]{"按钮", "摇杆"}, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    Config.Control control = Config.create(
                        which == 0 ? Config.TYPE_BUTTON : Config.TYPE_JOYSTICK);
                    config.controls.add(control);
                    saveConfig();
                    touchView.select(control.id);
                    applyConfig();
                }
            })
            .show();
    }

    private LinearLayout buildHintBar() {
        LinearLayout bar = new LinearLayout(host);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(rounded(0xE6282828, 18));
        bar.setPadding(dp(14), dp(8), dp(8), dp(8));

        TextView hint = text("拖动调整位置 · 双击键位修改按键信息", 13, TEXT, false);
        bar.addView(hint);
        bar.addView(action("添加", ACCENT, new Runnable() {
            @Override
            public void run() {
                showAddDialog();
            }
        }));
        bar.addView(action("完成", TEXT, new Runnable() {
            @Override
            public void run() {
                config.editing = false;
                saveConfig();
                applyConfig();
            }
        }));
        return bar;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(BG, 24));
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        return card;
    }

    private View titleRow(String title, final Runnable close) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(8));

        TextView titleView = text(title, 20, TEXT, true);
        row.addView(titleView, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(action("✕", TEXT, close));
        return row;
    }

    private View row(String title, String summary, final Runnable click) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.addView(text(title, 16, TEXT, false));
        if (summary != null) {
            row.addView(text(summary, 12, SUBTEXT, false));
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        return row;
    }

    private View rowDanger(String title, String summary, final Runnable click) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.addView(text(title, 16, DANGER, false));
        if (summary != null) {
            row.addView(text(summary, 12, SUBTEXT, false));
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        return row;
    }

    private View switchRow(String title, String summary, ToggleView toggle) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout textColumn = new LinearLayout(host);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.addView(text(title, 16, TEXT, false));
        textColumn.addView(text(summary, 12, SUBTEXT, false));
        row.addView(textColumn, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams toggleParams =
            new LinearLayout.LayoutParams(dp(72), dp(40));
        toggleParams.setMargins(dp(12), 0, 0, 0);
        row.addView(toggle, toggleParams);
        return row;
    }

    private View divider() {
        View divider = new View(host);
        divider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(dp(16), 0, dp(16), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private View sliderRow(String label, SeekBar bar, TextView value) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        row.addView(text(label, 13, TEXT, false));
        row.addView(bar, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value);
        return row;
    }

    private View colorRow(final Config.Control control) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        row.addView(text("颜色", 13, TEXT, false));

        int[] colors = new int[]{
            Color.WHITE,
            Color.rgb(180, 180, 180),
            Color.rgb(255, 82, 82),
            Color.rgb(255, 153, 0),
            Color.rgb(255, 221, 64),
            Color.rgb(76, 217, 100),
            Color.rgb(64, 220, 220),
            Color.rgb(64, 156, 255),
            Color.rgb(180, 120, 255),
            Color.rgb(255, 120, 200),
        };
        for (final int color : colors) {
            View swatch = new View(host);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke(dp(2), color == control.color ? ACCENT : Color.TRANSPARENT);
            swatch.setBackground(drawable);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(26), dp(26));
            params.setMargins(0, 0, dp(6), 0);
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    control.color = color;
                    for (int i = 0; i < row.getChildCount(); i++) {
                        View child = row.getChildAt(i);
                        if (child.getBackground() instanceof GradientDrawable) {
                            ((GradientDrawable) child.getBackground()).setStroke(
                                dp(2), child == v ? ACCENT : Color.TRANSPARENT);
                        }
                    }
                    touchView.invalidate();
                    saveConfig();
                }
            });
            row.addView(swatch);
        }
        return row;
    }

    private TextView action(String label, int color, final Runnable click) {
        TextView view = text(label, 14, color, false);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        return view;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView valueText(String value) {
        TextView view = text(value, 13, TEXT, false);
        view.setGravity(Gravity.END);
        view.setMinWidth(dp(48));
        view.setPadding(dp(8), 0, 0, 0);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * density);
    }

    private static void addKey(List<String> labels, List<Integer> codes,
                               String label, int code) {
        labels.add(label);
        codes.add(code);
    }

    private abstract static class SimpleSeekListener
        implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    /** Draggable ball that opens the menu. */
    private static final class BallView extends View {
        private final EngineOverlay overlay;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private float downRawX;
        private float downRawY;
        private float startX;
        private float startY;
        private boolean moved;

        BallView(Context context, EngineOverlay overlay) {
            super(context);
            this.overlay = overlay;
            this.density = getResources().getDisplayMetrics().density;
            setWillNotDraw(false);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = getWidth() / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(150, 0, 0, 0));
            canvas.drawCircle(cx, cy, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(Color.argb(200, 255, 255, 255));
            canvas.drawCircle(cx, cy, radius - 1.5f * density, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            float dot = 2.2f * density;
            canvas.drawCircle(cx - 6f * density, cy, dot, paint);
            canvas.drawCircle(cx, cy, dot, paint);
            canvas.drawCircle(cx + 6f * density, cy, dot, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = getX();
                    startY = getY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (Math.abs(dx) > 6f * density || Math.abs(dy) > 6f * density) {
                        moved = true;
                    }
                    if (moved) {
                        View parent = (View) getParent();
                        if (parent == null) return true;
                        float maxX = Math.max(0, parent.getWidth() - getWidth());
                        float maxY = Math.max(0, parent.getHeight() - getHeight());
                        setX(Math.max(0f, Math.min(maxX, startX + dx)));
                        setY(Math.max(0f, Math.min(maxY, startY + dy)));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    View parent = (View) getParent();
                    if (moved && parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
                        overlay.config.ballX = (getX() + getWidth() / 2f) / parent.getWidth();
                        overlay.config.ballY = (getY() + getHeight() / 2f) / parent.getHeight();
                        overlay.saveConfig();
                    } else {
                        performClick();
                        overlay.showMenu();
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
