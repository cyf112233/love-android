/*
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.love2d.android.touch

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TouchControlType {
    JOYSTICK,
    BUTTON,
}

data class TouchControl(
    val id: String,
    val type: TouchControlType,
    var x: Float,
    var y: Float,
    var sizeDp: Int,
    var color: Int,
    var alpha: Float,
    var keyCode: Int,
    var label: String,
) {
    val isJoystick: Boolean get() = type == TouchControlType.JOYSTICK
}

data class TouchLayout(
    var enabled: Boolean = false,
    var editing: Boolean = false,
    var ballX: Float = 0.92f,
    var ballY: Float = 0.45f,
    val controls: MutableList<TouchControl> = defaultControls(),
)

fun defaultControls(): MutableList<TouchControl> = mutableListOf(
    TouchControl(
        id = "joystick",
        type = TouchControlType.JOYSTICK,
        x = 0.15f,
        y = 0.78f,
        sizeDp = 150,
        color = Color.WHITE,
        alpha = 0.32f,
        keyCode = 0,
        label = "摇杆",
    ),
    TouchControl(
        id = "esc",
        type = TouchControlType.BUTTON,
        x = 0.08f,
        y = 0.12f,
        sizeDp = 60,
        color = Color.WHITE,
        alpha = 0.50f,
        keyCode = KeyEvent.KEYCODE_ESCAPE,
        label = "ESC",
    ),
    TouchControl(
        id = "z",
        type = TouchControlType.BUTTON,
        x = 0.76f,
        y = 0.80f,
        sizeDp = 64,
        color = Color.WHITE,
        alpha = 0.50f,
        keyCode = KeyEvent.KEYCODE_Z,
        label = "Z",
    ),
    TouchControl(
        id = "x",
        type = TouchControlType.BUTTON,
        x = 0.87f,
        y = 0.72f,
        sizeDp = 64,
        color = Color.WHITE,
        alpha = 0.50f,
        keyCode = KeyEvent.KEYCODE_X,
        label = "X",
    ),
    TouchControl(
        id = "c",
        type = TouchControlType.BUTTON,
        x = 0.87f,
        y = 0.88f,
        sizeDp = 64,
        color = Color.WHITE,
        alpha = 0.50f,
        keyCode = KeyEvent.KEYCODE_C,
        label = "C",
    ),
)

fun newControl(type: TouchControlType): TouchControl {
    val isJoystick = type == TouchControlType.JOYSTICK
    return TouchControl(
        id = UUID.randomUUID().toString(),
        type = type,
        x = 0.5f,
        y = 0.5f,
        sizeDp = if (isJoystick) 150 else 64,
        color = Color.WHITE,
        alpha = 0.50f,
        keyCode = if (isJoystick) 0 else KeyEvent.KEYCODE_Z,
        label = if (isJoystick) "摇杆" else "Z",
    )
}

/** Persists the touch control layout in SharedPreferences. */
class TouchSettingsRepository(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("touch_controls", Context.MODE_PRIVATE)

    fun load(): TouchLayout {
        val raw = preferences.getString(KEY_LAYOUT, null) ?: return TouchLayout()
        return runCatching {
            val json = JSONObject(raw)
            val layout = TouchLayout(
                enabled = json.optBoolean("enabled", false),
                editing = json.optBoolean("editing", false),
                ballX = json.optDouble("ballX", 0.92).toFloat().coerceIn(0.05f, 0.95f),
                ballY = json.optDouble("ballY", 0.45).toFloat().coerceIn(0.05f, 0.95f),
                controls = mutableListOf(),
            )
            val array = json.optJSONArray("controls")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    layout.controls.add(
                        TouchControl(
                            id = item.optString("id", UUID.randomUUID().toString()),
                            type = if (item.optString("type") == "joystick") {
                                TouchControlType.JOYSTICK
                            } else {
                                TouchControlType.BUTTON
                            },
                            x = item.optDouble("x", 0.5).toFloat(),
                            y = item.optDouble("y", 0.5).toFloat(),
                            sizeDp = item.optInt("size", 64),
                            color = item.optInt("color", Color.WHITE),
                            alpha = item.optDouble("alpha", 0.5).toFloat(),
                            keyCode = item.optInt("key", KeyEvent.KEYCODE_Z),
                            label = item.optString("label", "Z"),
                        )
                    )
                }
            } else {
                layout.controls.addAll(defaultControls())
            }
            layout
        }.getOrElse { TouchLayout() }
    }

    fun save(layout: TouchLayout) {
        val json = JSONObject()
        json.put("enabled", layout.enabled)
        json.put("editing", layout.editing)
        json.put("ballX", layout.ballX.toDouble())
        json.put("ballY", layout.ballY.toDouble())
        val array = JSONArray()
        for (control in layout.controls) {
            array.put(
                JSONObject().apply {
                    put("id", control.id)
                    put("type", if (control.isJoystick) "joystick" else "button")
                    put("x", control.x.toDouble())
                    put("y", control.y.toDouble())
                    put("size", control.sizeDp)
                    put("color", control.color)
                    put("alpha", control.alpha.toDouble())
                    put("key", control.keyCode)
                    put("label", control.label)
                }
            )
        }
        json.put("controls", array)
        preferences.edit().putString(KEY_LAYOUT, json.toString()).apply()
    }

    fun setEnabled(enabled: Boolean) {
        val layout = load()
        layout.enabled = enabled
        if (!enabled) layout.editing = false
        save(layout)
    }

    fun setEditing(editing: Boolean) {
        val layout = load()
        layout.editing = editing
        if (editing) layout.enabled = true
        save(layout)
    }

    private companion object {
        const val KEY_LAYOUT = "layout"
    }
}
