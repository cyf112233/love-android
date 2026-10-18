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
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

/**
 * Window overlay installed on top of the SDL surface:
 *
 *  - a draggable floating ball that opens the Miuix game menu,
 *  - the plain-View touch controls (joystick + buttons),
 *  - an edit mode with drag / add / delete / size / colour / alpha / key
 *    mapping.
 *
 * All of the frequently-updated parts are Android Views, not Compose.
 */
@SuppressLint("ViewConstructor")
class TouchControlsOverlay(private val host: Activity) : FrameLayout(host) {

    private val repository = TouchSettingsRepository(host)
    private var config: TouchLayout = repository.load()

    private val controlsView = TouchControlsView(host)
    private val ball = FloatingBallView(host)

    private lateinit var hintBar: LinearLayout
    private lateinit var editPanel: LinearLayout
    private lateinit var panelTitle: TextView
    private lateinit var sizeRow: LinearLayout
    private lateinit var alphaRow: LinearLayout
    private lateinit var colorRow: LinearLayout
    private lateinit var keyButton: TextView
    private lateinit var sizeBar: SeekBar
    private lateinit var alphaBar: SeekBar
    private lateinit var sizeValue: TextView
    private lateinit var alphaValue: TextView
    private lateinit var deleteButton: TextView

    private var selected: TouchControl? = null
    private var updatingPanel = false
    private var panelVisible = false

    init {
        isClickable = false
        setWillNotDraw(true)

        controlsView.layout = config
        addView(
            controlsView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        addView(ball, LayoutParams(dp(48), dp(48)))
        ball.onClick = { openMenu() }
        ball.onMoved = { nx, ny ->
            config.ballX = nx
            config.ballY = ny
            repository.save(config)
        }

        buildHintBar()
        addView(
            hintBar,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
            },
        )

        buildEditPanel()
        addView(
            editPanel,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(12)
                leftMargin = dp(12)
                rightMargin = dp(12)
            },
        )

        controlsView.onLayoutChanged = { repository.save(config) }
        controlsView.onControlSelected = { control ->
            selected = control
            if (control == null) {
                hidePanel()
            } else if (panelVisible) {
                refreshPanel()
            }
        }
        controlsView.onControlDoubleTapped = { control ->
            selected = control
            showPanel()
        }

        applyConfig()
        post { positionBall() }
    }

    fun onHostResume() {
        config = repository.load()
        controlsView.layout = config
        applyConfig()
        post { positionBall() }
    }

    fun onHostPause() {
        controlsView.releaseAll()
    }

    fun release() {
        controlsView.releaseAll()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        positionBall()
    }

    private fun applyConfig() {
        controlsView.layout = config
        controlsView.editing = config.editing
        hintBar.visibility = if (config.editing) View.VISIBLE else View.GONE
        hidePanel()
        selected = null
        refreshPanel()
        positionBall()
    }

    private fun showPanel() {
        if (!config.editing) return
        panelVisible = true
        editPanel.visibility = View.VISIBLE
        refreshPanel()
    }

    private fun hidePanel() {
        panelVisible = false
        editPanel.visibility = View.GONE
    }

    private fun positionBall() {
        if (width == 0 || height == 0 || ball.width == 0) {
            post { positionBall() }
            return
        }
        val maxX = (width - ball.width).coerceAtLeast(0).toFloat()
        val maxY = (height - ball.height).coerceAtLeast(0).toFloat()
        ball.x = (config.ballX * width - ball.width / 2f).coerceIn(0f, maxX)
        ball.y = (config.ballY * height - ball.height / 2f).coerceIn(0f, maxY)
    }

    private fun openMenu() {
        val intent = Intent().setClassName(
            host.packageName,
            "org.love2d.android.GameMenuActivity",
        )
        runCatching { host.startActivity(intent) }
    }

    private fun buildHintBar() {
        hintBar = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(225, 28, 28, 28), 18)
            setPadding(dp(14), dp(8), dp(8), dp(8))
        }

        val hint = TextView(host).apply {
            text = "拖动调整位置 · 双击键位修改按键信息"
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        hintBar.addView(hint)

        hintBar.addView(
            action("添加", Color.rgb(80, 170, 255)) { showAddDialog() },
        )
        hintBar.addView(
            action("完成", Color.WHITE) { finishEditing() },
        )
    }

    private fun buildEditPanel() {
        editPanel = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(235, 28, 28, 28), 20)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }

        panelTitle = TextView(host).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(panelTitle)
        header.addView(action("收起", Color.WHITE) { hidePanel() })
        editPanel.addView(header)

        sizeRow = row()
        sizeRow.addView(label("大小"))
        sizeBar = SeekBar(host).apply {
            max = MAX_SIZE_DP - MIN_SIZE_DP
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        sizeRow.addView(sizeBar)
        sizeValue = valueText()
        sizeRow.addView(sizeValue)
        editPanel.addView(sizeRow)

        alphaRow = row()
        alphaRow.addView(label("透明度"))
        alphaBar = SeekBar(host).apply {
            max = 90
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        alphaRow.addView(alphaBar)
        alphaValue = valueText()
        alphaRow.addView(alphaValue)
        editPanel.addView(alphaRow)

        colorRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        colorRow.addView(label("颜色"))
        for (color in SWATCHES) {
            colorRow.addView(makeSwatch(color))
        }
        editPanel.addView(colorRow)

        keyButton = action("触发键", Color.WHITE) { showKeyPicker() }
        editPanel.addView(keyButton)

        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(action("添加按钮", Color.WHITE) { addControl(TouchControlType.BUTTON) })
        actions.addView(action("添加摇杆", Color.WHITE) { addControl(TouchControlType.JOYSTICK) })
        deleteButton = action("删除", Color.rgb(255, 96, 96)) {
            controlsView.deleteSelected()
            selected = null
            hidePanel()
            refreshPanel()
        }
        actions.addView(deleteButton)
        editPanel.addView(actions)

        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updatingPanel) return
                val size = MIN_SIZE_DP + progress
                sizeValue.text = "${size}dp"
                controlsView.updateSelected { it.sizeDp = size }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        alphaBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updatingPanel) return
                val alpha = (10 + progress) / 100f
                alphaValue.text = "${10 + progress}%"
                controlsView.updateSelected { it.alpha = alpha }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun refreshPanel() {
        val control = selected
        panelTitle.text = when {
            control == null -> "未选择控件"
            control.isJoystick -> "摇杆"
            else -> "按钮 ${control.label}"
        }

        val visible = if (control != null) View.VISIBLE else View.GONE
        sizeRow.visibility = visible
        alphaRow.visibility = visible
        colorRow.visibility = visible
        keyButton.visibility = if (control != null && !control.isJoystick) {
            View.VISIBLE
        } else {
            View.GONE
        }
        deleteButton.isEnabled = control != null
        deleteButton.alpha = if (control != null) 1f else 0.4f

        if (control == null) return

        updatingPanel = true
        sizeBar.progress = (control.sizeDp - MIN_SIZE_DP).coerceIn(0, MAX_SIZE_DP - MIN_SIZE_DP)
        alphaBar.progress = ((control.alpha * 100).toInt() - 10).coerceIn(0, 90)
        sizeValue.text = "${control.sizeDp}dp"
        alphaValue.text = "${(control.alpha * 100).toInt()}%"
        keyButton.text = "触发键：${control.label}"
        updateSwatchSelection(control.color)
        updatingPanel = false
    }

    private fun updateSwatchSelection(selectedColor: Int) {
        for (index in 0 until colorRow.childCount) {
            val child = colorRow.getChildAt(index) ?: continue
            val color = child.tag as? Int ?: continue
            val drawable = child.background as? GradientDrawable ?: continue
            drawable.setStroke(
                dp(2),
                if (color == selectedColor) Color.rgb(80, 170, 255) else Color.TRANSPARENT,
            )
        }
    }

    private fun showAddDialog() {
        AlertDialog.Builder(host)
            .setTitle("添加控件")
            .setItems(arrayOf("按钮", "摇杆")) { _, which ->
                addControl(
                    if (which == 0) TouchControlType.BUTTON else TouchControlType.JOYSTICK,
                )
            }
            .show()
    }

    private fun addControl(type: TouchControlType) {
        val control = controlsView.addControl(type)
        selected = control
        refreshPanel()
    }

    private fun showKeyPicker() {
        val control = selected ?: return
        if (control.isJoystick) return
        val labels = KEY_OPTIONS.map { it.first }.toTypedArray()
        AlertDialog.Builder(host)
            .setTitle("触发键")
            .setItems(labels) { _, which ->
                val (label, keyCode) = KEY_OPTIONS[which]
                control.keyCode = keyCode
                control.label = label
                controlsView.invalidate()
                repository.save(config)
                refreshPanel()
            }
            .show()
    }

    private fun finishEditing() {
        config.editing = false
        controlsView.editing = false
        controlsView.select(null)
        repository.save(config)
        applyConfig()
    }

    private fun row(): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, 0)
    }

    private fun label(text: String): TextView = TextView(host).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13f
        setPadding(0, 0, dp(8), 0)
    }

    private fun valueText(): TextView = TextView(host).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        gravity = Gravity.END
        minWidth = dp(48)
        setPadding(dp(8), 0, 0, 0)
    }

    private fun action(
        text: String,
        color: Int,
        onClick: () -> Unit,
    ): TextView = TextView(host).apply {
        this.text = text
        setTextColor(color)
        textSize = 14f
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { onClick() }
    }

    private fun makeSwatch(color: Int): View = View(host).apply {
        tag = color
        layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
            marginEnd = dp(6)
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.TRANSPARENT)
        }
        setOnClickListener {
            controlsView.updateSelected { it.color = color }
            updateSwatchSelection(color)
        }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MIN_SIZE_DP = 32
        const val MAX_SIZE_DP = 240

        val SWATCHES = intArrayOf(
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
        )

        val KEY_OPTIONS: List<Pair<String, Int>> = listOf(
            "ESC" to KeyEvent.KEYCODE_ESCAPE,
            "Z" to KeyEvent.KEYCODE_Z,
            "X" to KeyEvent.KEYCODE_X,
            "C" to KeyEvent.KEYCODE_C,
            "A" to KeyEvent.KEYCODE_A,
            "S" to KeyEvent.KEYCODE_S,
            "D" to KeyEvent.KEYCODE_D,
            "W" to KeyEvent.KEYCODE_W,
            "Q" to KeyEvent.KEYCODE_Q,
            "E" to KeyEvent.KEYCODE_E,
            "R" to KeyEvent.KEYCODE_R,
            "F" to KeyEvent.KEYCODE_F,
            "SPACE" to KeyEvent.KEYCODE_SPACE,
            "ENTER" to KeyEvent.KEYCODE_ENTER,
            "SHIFT" to KeyEvent.KEYCODE_SHIFT_LEFT,
            "TAB" to KeyEvent.KEYCODE_TAB,
            "↑" to KeyEvent.KEYCODE_DPAD_UP,
            "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
            "←" to KeyEvent.KEYCODE_DPAD_LEFT,
            "→" to KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }
}

/** Draggable floating ball; tap opens the game menu. */
@SuppressLint("ViewConstructor")
private class FloatingBallView(context: Context) : View(context) {
    var onClick: (() -> Unit)? = null
    var onMoved: ((Float, Float) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var moved = false

    init {
        setWillNotDraw(false)
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = width / 2f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius - 1.5f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val dot = 2.2f * density
        canvas.drawCircle(cx - 6f * density, cy, dot, paint)
        canvas.drawCircle(cx, cy, dot, paint)
        canvas.drawCircle(cx + 6f * density, cy, dot, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = x
                startY = y
                moved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) > 6f * density || abs(dy) > 6f * density) moved = true
                if (moved) {
                    val parentWidth = (parent as? View)?.width ?: return true
                    val parentHeight = (parent as? View)?.height ?: return true
                    x = (startX + dx).coerceIn(0f, (parentWidth - width).coerceAtLeast(0).toFloat())
                    y = (startY + dy).coerceIn(0f, (parentHeight - height).coerceAtLeast(0).toFloat())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (moved) {
                    val parentView = parent as? View
                    if (parentView != null && parentView.width > 0 && parentView.height > 0) {
                        val nx = (x + width / 2f) / parentView.width
                        val ny = (y + height / 2f) / parentView.height
                        onMoved?.invoke(nx, ny)
                    }
                } else {
                    performClick()
                    onClick?.invoke()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
