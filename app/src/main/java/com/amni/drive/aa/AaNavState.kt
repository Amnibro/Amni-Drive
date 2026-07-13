package com.amni.drive.aa
import com.amni.drive.map.Place
import com.amni.drive.map.RouteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
object AaNavState {
@Volatile var destination: Place? = null
@Volatile var route: RouteResult? = null
@Volatile var lastQuery: String = ""
private val _searchPulse = MutableStateFlow(0L)
val searchPulse: StateFlow<Long> = _searchPulse.asStateFlow()
private val _mediaPulse = MutableStateFlow(0L)
val mediaPulse: StateFlow<Long> = _mediaPulse.asStateFlow()
private val _hostSearchPulse = MutableStateFlow(0L)
val hostSearchPulse: StateFlow<Long> = _hostSearchPulse.asStateFlow()
private val _pendingGo = MutableStateFlow<Place?>(null)
val pendingGo: StateFlow<Place?> = _pendingGo.asStateFlow()
fun clear() { destination = null; route = null }
fun openSearch() { lastQuery = lastQuery; _searchPulse.value = System.currentTimeMillis() }
fun openMedia() { _mediaPulse.value = System.currentTimeMillis() }
fun openHostSearch() { _hostSearchPulse.value = System.currentTimeMillis() }
fun requestGo(p: Place) { _pendingGo.value = p }
fun consumeGo() { _pendingGo.value = null }
}
