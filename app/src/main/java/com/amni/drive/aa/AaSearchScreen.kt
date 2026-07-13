package com.amni.drive.aa
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.amni.drive.AmniDriveApp
import com.amni.drive.map.Geocoder
import com.amni.drive.map.Place as AmniPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
class AaSearchScreen(carContext: CarContext) : Screen(carContext) {
private val scope = CoroutineScope(Dispatchers.Main + Job())
private var query = AaNavState.lastQuery
private var results: List<AmniPlace> = emptyList()
private var loading = false
private var message = "Type a place or address"
override fun onGetTemplate(): Template {
val app = carContext.applicationContext as? AmniDriveApp
val loc = app?.tracker?.location?.value
val list = ItemList.Builder()
if (results.isEmpty()) {
list.setNoItemsMessage(message)
} else {
results.take(6).forEach { p ->
val km = if (loc != null) haversineKm(loc.latitude, loc.longitude, p.lat, p.lon) else 0.0
val title = buildSpannedString {
inSpans(DistanceSpan.create(Distance.create(km, Distance.UNIT_KILOMETERS))) { append(" ") }
append(p.name.ifBlank { "Place" })
}
val row = Row.Builder()
.setTitle(title)
.addText(p.detail.ifBlank { "${p.lat}, ${p.lon}" })
.setMetadata(
Metadata.Builder().setPlace(
Place.Builder(CarLocation.create(p.lat, p.lon))
.setMarker(PlaceMarker.Builder().build())
.build()
).build()
)
.setOnClickListener {
AaNavState.lastQuery = query
AaNavState.requestGo(p)
screenManager.pop()
}
.build()
list.addItem(row)
}
}
return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
override fun onSearchTextChanged(searchText: String) {
query = searchText
}
override fun onSearchSubmitted(searchText: String) {
query = searchText
runSearch()
}
})
.setHeaderAction(Action.BACK)
.setShowKeyboardByDefault(true)
.setSearchHint("Address, place, gas…")
.setInitialSearchText(query)
.setLoading(loading)
.setItemList(list.build())
.build()
}
private fun runSearch() {
if (query.isBlank()) {
message = "Type something first"
results = emptyList()
invalidate()
return
}
loading = true
message = "Searching…"
results = emptyList()
invalidate()
scope.launch {
try {
val app = carContext.applicationContext as? AmniDriveApp
val loc = app?.tracker?.location?.value
results = Geocoder.search(query, loc?.latitude, loc?.longitude)
AaNavState.lastQuery = query
message = if (results.isEmpty()) "No matches for \"$query\"" else "${results.size} results"
} catch (e: Exception) {
results = emptyList()
message = "Search failed · ${e.message ?: "try again"}"
}
loading = false
invalidate()
}
}
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
val r = 6371.0
val dLat = Math.toRadians(lat2 - lat1)
val dLon = Math.toRadians(lon2 - lon1)
val a = sin(dLat / 2).let { it * it } + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
return 2 * r * atan2(sqrt(a), sqrt(1 - a))
}
}
