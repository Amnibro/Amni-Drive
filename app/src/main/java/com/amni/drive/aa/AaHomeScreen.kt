package com.amni.drive.aa
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.amni.drive.AmniDriveApp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
class AaHomeScreen(carContext: CarContext) : Screen(carContext) {
init {
lifecycle.addObserver(object : DefaultLifecycleObserver {
override fun onStart(owner: LifecycleOwner) {
runCatching { (carContext.applicationContext as? AmniDriveApp)?.tracker?.start() }
}
})
lifecycle.coroutineScope.launch {
AaNavState.hostSearchPulse.collect {
if (it > 0L && screenManager.top === this@AaHomeScreen) runCatching { screenManager.push(AaSearchScreen(carContext)) }
}
}
}
override fun onGetTemplate(): Template {
return NavigationTemplate.Builder()
.setActionStrip(
ActionStrip.Builder()
.addAction(Action.APP_ICON)
.build()
)
.setMapActionStrip(
ActionStrip.Builder()
.addAction(Action.PAN)
.build()
)
.build()
}
companion object {
fun formatRoute(m: Double, s: Double): String {
val mi = m / 1609.344
val min = (s / 60.0).roundToInt()
return if (mi >= 10) "${mi.roundToInt()} mi · $min min" else "${"%.1f".format(mi)} mi · $min min"
}
}
}
