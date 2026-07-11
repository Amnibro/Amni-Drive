package com.amni.drive.aa
import com.amni.drive.map.Place
import com.amni.drive.map.RouteResult
object AaNavState {
@Volatile var destination: Place? = null
@Volatile var route: RouteResult? = null
@Volatile var lastQuery: String = ""
fun clear() { destination = null; route = null }
}
