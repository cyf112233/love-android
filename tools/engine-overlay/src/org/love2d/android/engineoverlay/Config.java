package org.love2d.android.engineoverlay;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Touch layout persisted per engine pack. */
final class Config {
    static final int TYPE_JOYSTICK = 0;
    static final int TYPE_BUTTON = 1;

    boolean enabled;
    boolean editing;
    float ballX = 0.92f;
    float ballY = 0.45f;
    final List<Control> controls = new ArrayList<>();

    static final class Control {
        String id;
        int type;
        float x;
        float y;
        int sizeDp;
        int color;
        float alpha;
        int key;
        String label;

        boolean isJoystick() {
            return type == TYPE_JOYSTICK;
        }
    }

    static Config load(SharedPreferences preferences) {
        String raw = preferences.getString("layout", null);
        if (raw == null) {
            Config config = new Config();
            config.controls.addAll(defaults());
            return config;
        }
        try {
            JSONObject json = new JSONObject(raw);
            Config config = new Config();
            config.enabled = json.optBoolean("enabled", false);
            config.editing = json.optBoolean("editing", false);
            config.ballX = (float) json.optDouble("ballX", 0.92);
            config.ballY = (float) json.optDouble("ballY", 0.45);
            JSONArray array = json.optJSONArray("controls");
            if (array == null) {
                config.controls.addAll(defaults());
            } else {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    Control control = new Control();
                    control.id = item.optString("id", UUID.randomUUID().toString());
                    control.type = item.optString("type").equals("joystick")
                        ? TYPE_JOYSTICK : TYPE_BUTTON;
                    control.x = (float) item.optDouble("x", 0.5);
                    control.y = (float) item.optDouble("y", 0.5);
                    control.sizeDp = item.optInt("size", 64);
                    control.color = item.optInt("color", Color.WHITE);
                    control.alpha = (float) item.optDouble("alpha", 0.5);
                    control.key = item.optInt("key", KeyEvent.KEYCODE_Z);
                    control.label = item.optString("label", "Z");
                    config.controls.add(control);
                }
            }
            return config;
        } catch (Throwable ignored) {
            Config config = new Config();
            config.controls.addAll(defaults());
            return config;
        }
    }

    void save(SharedPreferences preferences) {
        try {
            JSONObject json = new JSONObject();
            json.put("enabled", enabled);
            json.put("editing", editing);
            json.put("ballX", ballX);
            json.put("ballY", ballY);
            JSONArray array = new JSONArray();
            for (Control control : controls) {
                JSONObject item = new JSONObject();
                item.put("id", control.id);
                item.put("type", control.isJoystick() ? "joystick" : "button");
                item.put("x", control.x);
                item.put("y", control.y);
                item.put("size", control.sizeDp);
                item.put("color", control.color);
                item.put("alpha", control.alpha);
                item.put("key", control.key);
                item.put("label", control.label);
                array.put(item);
            }
            json.put("controls", array);
            preferences.edit().putString("layout", json.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    static List<Control> defaults() {
        List<Control> list = new ArrayList<>();
        list.add(control("joystick", TYPE_JOYSTICK, 0.15f, 0.78f, 150,
            Color.WHITE, 0.32f, 0, "摇杆"));
        list.add(control("esc", TYPE_BUTTON, 0.08f, 0.12f, 60,
            Color.WHITE, 0.50f, KeyEvent.KEYCODE_ESCAPE, "ESC"));
        list.add(control("z", TYPE_BUTTON, 0.76f, 0.80f, 64,
            Color.WHITE, 0.50f, KeyEvent.KEYCODE_Z, "Z"));
        list.add(control("x", TYPE_BUTTON, 0.87f, 0.72f, 64,
            Color.WHITE, 0.50f, KeyEvent.KEYCODE_X, "X"));
        list.add(control("c", TYPE_BUTTON, 0.87f, 0.88f, 64,
            Color.WHITE, 0.50f, KeyEvent.KEYCODE_C, "C"));
        return list;
    }

    static Control create(int type) {
        Control control = new Control();
        control.id = UUID.randomUUID().toString();
        control.type = type;
        control.x = 0.5f;
        control.y = 0.5f;
        control.sizeDp = type == TYPE_JOYSTICK ? 150 : 64;
        control.color = Color.WHITE;
        control.alpha = 0.5f;
        control.key = type == TYPE_JOYSTICK ? 0 : KeyEvent.KEYCODE_Z;
        control.label = type == TYPE_JOYSTICK ? "摇杆" : "Z";
        return control;
    }

    private static Control control(String id, int type, float x, float y, int size,
                                   int color, float alpha, int key, String label) {
        Control control = new Control();
        control.id = id;
        control.type = type;
        control.x = x;
        control.y = y;
        control.sizeDp = size;
        control.color = color;
        control.alpha = alpha;
        control.key = key;
        control.label = label;
        return control;
    }
}
