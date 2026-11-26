package com.amni.drive.debug
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.amni.drive.wire.AmniProjectionEngine
class CarPreviewActivity : Activity() {
    private var engine: AmniProjectionEngine? = null
    private var size: Pair<Int, Int>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = SurfaceView(this)
        sv.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { if (size == width to height) return; size = width to height; engine?.stop(); engine = AmniProjectionEngine(this@CarPreviewActivity, width, height, 160, 30, intent.getIntExtra("wm", 0), intent.getIntExtra("hm", 0), holder.surface).also { e -> Thread { e.start() }.start() } }
            override fun surfaceDestroyed(holder: SurfaceHolder) { engine?.stop(); engine = null; size = null }
        })
        sv.setOnTouchListener { _, e -> e.actionMasked.takeIf { it in setOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL) }?.let { a -> engine?.injectTouch(e.getX(0), e.getY(0), a, e.downTime, e.eventTime) }; true }
        setContentView(sv)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (event.keyCode in setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) super.dispatchKeyEvent(event) else engine?.let { it.dispatchKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN); true } ?: super.dispatchKeyEvent(event)
}
