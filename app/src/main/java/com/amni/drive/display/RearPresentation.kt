package com.amni.drive.display
import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
class RearPresentation(context: Context, display: Display, private val content: @Composable () -> Unit) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent { content() }
        setContentView(view)
    }
}
