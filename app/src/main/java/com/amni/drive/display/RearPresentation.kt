package com.amni.drive.display
import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
class RearPresentation(
context: Context,
display: Display,
private val content: @Composable () -> Unit
) : Presentation(context, display), LifecycleOwner, SavedStateRegistryOwner {
private val lifecycleRegistry = LifecycleRegistry(this)
private val savedStateController = SavedStateRegistryController.create(this)
override val lifecycle: Lifecycle get() = lifecycleRegistry
override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
savedStateController.performRestore(null)
lifecycleRegistry.currentState = Lifecycle.State.CREATED
val view = ComposeView(context)
view.setViewTreeLifecycleOwner(this)
view.setViewTreeSavedStateRegistryOwner(this)
view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
view.setContent { content() }
setContentView(view)
setCancelable(false)
lifecycleRegistry.currentState = Lifecycle.State.RESUMED
}
override fun onStart() {
super.onStart()
if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
lifecycleRegistry.currentState = Lifecycle.State.STARTED
lifecycleRegistry.currentState = Lifecycle.State.RESUMED
}
}
override fun onStop() {
if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
lifecycleRegistry.currentState = Lifecycle.State.CREATED
}
super.onStop()
}
override fun dismiss() {
runCatching {
if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) {
lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
}
}
super.dismiss()
}
}
