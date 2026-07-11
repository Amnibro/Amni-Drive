package com.amni.drive.aa
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import com.amni.drive.AmniDriveApp
import com.amni.drive.map.Geocoder
import com.amni.drive.map.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
class AaSearchScreen(carContext: CarContext) : Screen(carContext) {
private val scope = CoroutineScope(Dispatchers.Main + Job())
private var query = AaNavState.lastQuery
private var results: List<Place> = emptyList()
private var loading = false
private var message = "Type a place or address"
override fun onGetTemplate(): Template {
val list = ItemList.Builder()
if (results.isEmpty()) list.setNoItemsMessage(message)
else results.forEach { p ->
list.addItem(
Row.Builder()
.setTitle(p.name)
.addText(p.detail)
.setOnClickListener {
AaNavState.lastQuery = query
screenManager.push(AaRouteScreen(carContext, p))
}
.build()
)
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
invalidate()
return
}
loading = true
message = "Searching…"
results = emptyList()
invalidate()
scope.launch {
val app = carContext.applicationContext as? AmniDriveApp
val loc = app?.tracker?.location?.value
results = Geocoder.search(query, loc?.latitude, loc?.longitude)
AaNavState.lastQuery = query
message = if (results.isEmpty()) "No matches for \"$query\"" else "${results.size} results"
loading = false
invalidate()
}
}
}
