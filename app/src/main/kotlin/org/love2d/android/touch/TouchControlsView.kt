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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import org.libsdl.app.SDLActivity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The in-game touch controls. This is deliberately a plain [View]: it sits on
 * top of the SDL surface, so it must stay cheap to draw and handle touches
 * without going through Compose.
 */
@SuppressLint("ViewConstructor")
class TouchControlsView(context: Context) : View(context) {

    var layout: TouchLayout? = null
        set(value) {
            field = value
            selectedId = null
            invalidate()
        }

    var editing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                releaseAll()
                selectedId = null
            }
            invalidate()
        }

    var onControlSelected: ((TouchControl?) -> Unit)? = null
    var onControlDoubleTapped: ((TouchControl) -> Unit)? = null
    var onLayoutChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var selectedId: String? = null

    private val pressedButtons = mutableMapOf<Int, TouchControl>()
    private var joystickPointer = -1
    private var joystickControl: TouchControl? = null
    private var joystickKeys = mutableSetOf<Int>()
    private var joystickKnobX = 0f
    private var joystickKnobY = 0f

    private var dragPointer = -1
    private var dragControl: TouchControl? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dragMoved = false
    private var downX = 0f
    private var downY = 0f
    private var pendingDoubleControl: TouchControl? = null
    private var lastTapId: String? = null
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        setWillNotDraw(false)
    }

    fun selectedControl(): TouchControl? =
        layout?.controls?.firstOrNull { it.id == selectedId }

    fun addControl(type: TouchControlType): TouchControl? {
        val current = layout ?: return null
        val control = newControl(type)
        current.controls.add(control)
        selectedId = control.id
        onLayoutChanged?.invoke()
        onControlSelected?.invoke(control)
        invalidate()
        return control
    }

    fun deleteSelected() {
        val current = layout ?: return
        val control = selectedControl() ?: return
        current.controls.remove(control)
        selectedId = null
        onControlSelected?.invoke(null)
        onLayoutChanged?.invoke()
        invalidate()
    }

    fun updateSelected(change: (TouchControl) -> Unit) {
        val control = selectedControl() ?: return
        change(control)
        onLayoutChanged?.invoke()
        invalidate()
    }

    fun select(id: String?) {
        selectedId = id
        onControlSelected?.invoke(selectedControl())
        invalidate()
    }

    fun releaseAll() {
        for (control in pressedButtons.values) {
            sendKeyUp(control.keyCode)
        }
        pressedButtons.clear()
        releaseJoystick()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        releaseAll()
    }

    override fun onDetachedFromWindow() {
        releaseAll()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = layout ?: return
        if (!current.enabled && !editing) return

        for (control in current.controls) {
            drawControl(canvas, control)
        }
    }

    private fun drawControl(canvas: Canvas, control: TouchControl) {
        val cx = control.x * width
        val cy = control.y * height
        val radius = control.sizeDp * density / 2f

        paint.style = Paint.Style.FILL
        paint.color = control.color
        paint.alpha = (control.alpha.coerceIn(0.05f, 1f) * 255).toInt()
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.WHITE
        paint.alpha = 110
        canvas.drawCircle(cx, cy, radius, paint)

        if (control.isJoystick) {
            drawJoystick(canvas, control, cx, cy, radius)
        } else {
            textPaint.color = if (isLight(control.color)) Color.BLACK else Color.WHITE
            textPaint.alpha = 230
            textPaint.textSize = radius * 0.82f
            val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(control.label.take(3), cx, baseline, textPaint)
        }

        if (editing) {
            val selected = control.id == selectedId
            if (selected) {
                selectedPaint.strokeWidth = 3f * density
                selectedPaint.color = Color.rgb(80, 170, 255)
                selectedPaint.alpha = 255
                canvas.drawCircle(cx, cy, radius + 6f * density, selectedPaint)
            } else {
                dashedPaint.strokeWidth = 1.5f * density
                dashedPaint.color = Color.WHITE
                dashedPaint.alpha = 140
                canvas.drawCircle(cx, cy, radius + 6f * density, dashedPaint)
            }
        }
    }

    private fun drawJoystick(
        canvas: Canvas,
        control: TouchControl,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        val knobRadius = radius * 0.42f
        val active = joystickControl?.id == control.id
        val knobX = if (active) joystickKnobX else cx
        val knobY = if (active) joystickKnobY else cy

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = (control.alpha.coerceIn(0.05f, 1f) * 200).toInt()
        canvas.drawCircle(cx, cy, radius * 0.70f, paint)

        paint.color = control.color
        paint.alpha = (control.alpha.coerceIn(0.05f, 1f) * 255).toInt()
        canvas.drawCircle(knobX, knobY, knobRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.WHITE
        paint.alpha = 190
        canvas.drawCircle(knobX, knobY, knobRadius, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val current = layout ?: return false
        if (!current.enabled && !editing) return false

        if (editing) {
            return handleEditTouch(event)
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handlePointerDown(event, 0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                handlePointerDown(event, event.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(event)
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                handlePointerUp(event.getPointerId(event.actionIndex))
                true
            }
            MotionEvent.ACTION_UP -> {
                handlePointerUp(event.getPointerId(event.actionIndex))
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                releaseAll()
                true
            }
            else -> false
        }
    }

    private fun handleEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val control = findControl(event.x, event.y)
                dragMoved = false
                pendingDoubleControl = null
                if (control != null) {
                    val now = SystemClock.uptimeMillis()
                    val isDouble = control.id == lastTapId &&
                        now - lastTapAt in 0..DOUBLE_TAP_TIMEOUT_MS &&
                        abs(event.x - lastTapX) <= DOUBLE_TAP_SLOP_DP * density &&
                        abs(event.y - lastTapY) <= DOUBLE_TAP_SLOP_DP * density

                    selectedId = control.id
                    onControlSelected?.invoke(control)
                    if (isDouble) {
                        // Open the property panel when the second finger is
                        // lifted, not while it is still down.
                        pendingDoubleControl = control
                        lastTapId = null
                        lastTapAt = 0L
                        dragControl = null
                        dragPointer = -1
                    } else {
                        dragPointer = event.getPointerId(0)
                        dragControl = control
                        dragOffsetX = event.x - control.x * width
                        dragOffsetY = event.y - control.y * height
                        downX = event.x
                        downY = event.y
                    }
                    invalidate()
                } else {
                    selectedId = null
                    dragControl = null
                    pendingDoubleControl = null
                    lastTapId = null
                    lastTapAt = 0L
                    onControlSelected?.invoke(null)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val control = dragControl ?: return true
                val index = event.findPointerIndex(dragPointer)
                if (index < 0) return true
                if (!dragMoved) {
                    // Do not turn a normal tap with a little finger jitter into
                    // a drag: that would prevent the second tap being seen as a
                    // double tap.
                    if (hypot(event.getX(index) - downX, event.getY(index) - downY) <=
                        8f * density
                    ) {
                        return true
                    }
                    dragMoved = true
                }
                val newX = (event.getX(index) - dragOffsetX) / width
                val newY = (event.getY(index) - dragOffsetY) / height
                control.x = newX.coerceIn(0.04f, 0.96f)
                control.y = newY.coerceIn(0.04f, 0.96f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val control = dragControl
                val pending = pendingDoubleControl
                pendingDoubleControl = null
                if (pending != null && event.actionMasked != MotionEvent.ACTION_CANCEL) {
                    onControlDoubleTapped?.invoke(pending)
                } else if (dragMoved && control != null) {
                    onLayoutChanged?.invoke()
                } else if (control != null) {
                    // Remember this tap so the next tap on the same control
                    // can be recognised as a double tap.
                    lastTapId = control.id
                    lastTapAt = SystemClock.uptimeMillis()
                    lastTapX = event.x
                    lastTapY = event.y
                }
                dragControl = null
                dragPointer = -1
                dragMoved = false
                return true
            }
        }
        return true
    }

    private fun handlePointerDown(event: MotionEvent, index: Int): Boolean {
        val control = findControl(event.getX(index), event.getY(index)) ?: return false
        val pointerId = event.getPointerId(index)

        if (control.isJoystick) {
            if (joystickPointer != -1) return true
            joystickPointer = pointerId
            joystickControl = control
            updateJoystick(control, event.getX(index), event.getY(index))
        } else {
            pressedButtons[pointerId] = control
            sendKeyDown(control.keyCode)
        }
        invalidate()
        return true
    }

    private fun handleMove(event: MotionEvent) {
        val control = joystickControl ?: return
        val index = event.findPointerIndex(joystickPointer)
        if (index >= 0) {
            updateJoystick(control, event.getX(index), event.getY(index))
        }
    }

    private fun handlePointerUp(pointerId: Int) {
        if (pointerId == joystickPointer) {
            releaseJoystick()
        }
        pressedButtons.remove(pointerId)?.let { sendKeyUp(it.keyCode) }
        invalidate()
    }

    private fun updateJoystick(control: TouchControl, x: Float, y: Float) {
        val cx = control.x * width
        val cy = control.y * height
        val radius = control.sizeDp * density / 2f
        var dx = x - cx
        var dy = y - cy
        val distance = hypot(dx, dy)
        if (distance > radius) {
            dx = dx / distance * radius
            dy = dy / distance * radius
        }
        joystickKnobX = cx + dx
        joystickKnobY = cy + dy

        val newKeys = mutableSetOf<Int>()
        if (distance > radius * 0.22f) {
            val angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
            when {
                angle >= -22.5 && angle < 22.5 -> newKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT)
                angle >= 22.5 && angle < 67.5 -> {
                    newKeys.add(KeyEvent.KEYCODE_DPAD_UP)
                    newKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
                angle >= 67.5 && angle < 112.5 -> newKeys.add(KeyEvent.KEYCODE_DPAD_UP)
                angle >= 112.5 && angle < 157.5 -> {
                    newKeys.add(KeyEvent.KEYCODE_DPAD_UP)
                    newKeys.add(KeyEvent.KEYCODE_DPAD_LEFT)
                }
                angle >= 157.5 || angle < -157.5 -> newKeys.add(KeyEvent.KEYCODE_DPAD_LEFT)
                angle >= -157.5 && angle < -112.5 -> {
                    newKeys.add(KeyEvent.KEYCODE_DPAD_DOWN)
                    newKeys.add(KeyEvent.KEYCODE_DPAD_LEFT)
                }
                angle >= -112.5 && angle < -67.5 -> newKeys.add(KeyEvent.KEYCODE_DPAD_DOWN)
                else -> {
                    newKeys.add(KeyEvent.KEYCODE_DPAD_DOWN)
                    newKeys.add(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
            }
        }

        for (key in joystickKeys - newKeys) sendKeyUp(key)
        for (key in newKeys - joystickKeys) sendKeyDown(key)
        joystickKeys = newKeys
        invalidate()
    }

    private fun releaseJoystick() {
        for (key in joystickKeys) sendKeyUp(key)
        joystickKeys.clear()
        joystickPointer = -1
        joystickControl = null
        invalidate()
    }

    private fun findControl(x: Float, y: Float): TouchControl? {
        val current = layout ?: return null
        val slop = 12f * density
        for (control in current.controls.asReversed()) {
            val cx = control.x * width
            val cy = control.y * height
            val radius = control.sizeDp * density / 2f + slop
            if (abs(x - cx) <= radius && abs(y - cy) <= radius) {
                if (hypot(x - cx, y - cy) <= radius) return control
            }
        }
        return null
    }

    private fun sendKeyDown(keyCode: Int) {
        if (keyCode == 0) return
        runCatching { SDLActivity.onNativeKeyDown(keyCode) }
    }

    private fun sendKeyUp(keyCode: Int) {
        if (keyCode == 0) return
        runCatching { SDLActivity.onNativeKeyUp(keyCode) }
    }

    private fun isLight(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 > 150
    }

    private companion object {
        const val DOUBLE_TAP_TIMEOUT_MS = 450L
        const val DOUBLE_TAP_SLOP_DP = 32f
    }
}
