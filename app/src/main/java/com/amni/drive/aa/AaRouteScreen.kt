package com.amni.drive.aa
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.amni.drive.AmniDriveApp
import com.amni.drive.map.OsrmRouting
import com.amni.drive.map.Place
import com.amni.drive.map.RoutePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
class AaRouteScreen(carContext: CarContext, private val place: Place) : Screen(carContext) {
private val scope = CoroutineScope(Dispatchers.Main + Job())
private var loading = true
private var error: String? = null
private var summary = ""
init { compute() }
private fun compute() {
loading = true
error = null
invalidate()
scope.launch {
val app = carContext.applicationContext as? AmniDriveApp
app?.tracker?.start()
val loc = app?.tracker?.location?.value
if (loc == null) {
error = "No GPS yet — wait for a fix on the phone, then try again"
loading = false
invalidate()
return@launch
}
val route = OsrmRouting.route(
listOf(RoutePoint(loc.latitude, loc.longitude), RoutePoint(place.lat, place.lon))
)
if (route == null) {
error = "Could not build a route (need phone data)"
loading = false
invalidate()
return@launch
}
AaNavState.destination = place
AaNavState.route = route
summary = AaHomeScreen.formatRoute(route.distanceM, route.durationS)
loading = false
invalidate()
}
}
override fun onGetTemplate(): Template {
if (loading) {
return MessageTemplate.Builder("Routing to ${place.name}…")
.setHeaderAction(Action.BACK)
.setLoading(true)
.build()
}
error?.let { err ->
return MessageTemplate.Builder(err)
.setHeaderAction(Action.BACK)
.setTitle("Route")
.addAction(Action.Builder().setTitle("Retry").setOnClickListener { compute() }.build())
.build()
}
val pane = Pane.Builder()
.addRow(Row.Builder().setTitle(place.name).addText(place.detail.ifEmpty { "Destination" }).build())
.addRow(Row.Builder().setTitle(summary).addText("OpenStreetMap · OSRM").build())
.addAction(Action.Builder().setTitle("Start").setOnClickListener { screenManager.popToRoot() }.build())
.addAction(Action.Builder().setTitle("Clear").setOnClickListener { AaNavState.clear(); screenManager.popToRoot() }.build())
.build()
return PaneTemplate.Builder(pane)
.setTitle("Route ready")
.setHeaderAction(Action.BACK)
.build()
}
}
