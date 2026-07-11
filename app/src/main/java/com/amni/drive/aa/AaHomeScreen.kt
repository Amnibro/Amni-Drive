package com.amni.drive.aa
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.amni.drive.AmniDriveApp
import com.amni.drive.map.Geocoder
import com.amni.drive.map.Place as AmniPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
class AaHomeScreen(carContext: CarContext) : Screen(carContext) {
private val scope = CoroutineScope(Dispatchers.Main + Job())
private var nearby: List<AmniPlace> = emptyList()
private var loading = true
init {
lifecycle.addObserver(object : DefaultLifecycleObserver {
override fun onStart(owner: LifecycleOwner) { refreshNearby() }
})
}
private fun refreshNearby() {
loading = true
invalidate()
scope.launch {
val app = carContext.applicationContext as? AmniDriveApp
val loc = app?.tracker?.location?.value
app?.tracker?.start()
nearby = if (loc != null) {
Geocoder.search("coffee", loc.latitude, loc.longitude).ifEmpty {
Geocoder.search("gas", loc.latitude, loc.longitude)
}
} else Geocoder.search("coffee", null, null)
loading = false
invalidate()
}
}
override fun onGetTemplate(): Template {
val list = ItemList.Builder()
if (nearby.isEmpty()) {
list.setNoItemsMessage(if (loading) "Loading…" else "Tap Search to find a destination")
} else {
nearby.forEach { p ->
list.addItem(
Row.Builder()
.setTitle(p.name)
.addText(p.detail.ifEmpty { "${p.lat.format4()}, ${p.lon.format4()}" })
.setMetadata(
Metadata.Builder().setPlace(
Place.Builder(CarLocation.create(p.lat, p.lon))
.setMarker(PlaceMarker.Builder().build())
.build()
).build()
)
.setOnClickListener { screenManager.push(AaRouteScreen(carContext, p)) }
.build()
)
}
}
val dest = AaNavState.destination
val route = AaNavState.route
if (dest != null && route != null) {
list.addItem(
Row.Builder()
.setTitle("Active: ${dest.name}")
.addText(formatRoute(route.distanceM, route.durationS))
.setOnClickListener { screenManager.push(AaRouteScreen(carContext, dest)) }
.build()
)
}
return PlaceListNavigationTemplate.Builder()
.setTitle("Amni Drive")
.setHeaderAction(Action.APP_ICON)
.setActionStrip(
ActionStrip.Builder()
.addAction(Action.Builder().setTitle("Search").setOnClickListener { screenManager.push(AaSearchScreen(carContext)) }.build())
.addAction(Action.Builder().setTitle("Refresh").setOnClickListener { refreshNearby() }.build())
.build()
)
.setItemList(list.build())
.setLoading(loading)
.build()
}
companion object {
fun formatRoute(m: Double, s: Double): String {
val mi = m / 1609.344
val min = (s / 60.0).roundToInt()
return if (mi >= 10) "${mi.roundToInt()} mi · $min min" else "${"%.1f".format(mi)} mi · $min min"
}
private fun Double.format4() = "%.4f".format(this)
}
}
