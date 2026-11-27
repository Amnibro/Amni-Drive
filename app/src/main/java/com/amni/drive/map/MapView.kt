package com.amni.drive.map

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.roundToInt

class MapCamera(lat: Double, lon: Double) {
    var lat by mutableDoubleStateOf(lat)
    var lon by mutableDoubleStateOf(lon)
    var zoom by mutableIntStateOf(16)
    var follow by mutableStateOf(true)
    var northUp by mutableStateOf(false)
    var tilt by mutableStateOf(true)
    var basemap by mutableIntStateOf(0)
}

@Composable
fun rememberMapCamera(): MapCamera = remember { MapCamera(30.2672, -97.7431) }

private const val SRC_ROUTE = "amni-route-src"
private const val LYR_ROUTE_CASING = "amni-route-casing"
private const val LYR_ROUTE_LINE = "amni-route-line"
private const val SRC_STOPS = "amni-stops-src"
private const val LYR_STOPS = "amni-stops-lyr"
private const val SRC_DEST = "amni-dest-src"
private const val LYR_DEST = "amni-dest-lyr"
private const val SRC_REPORTS = "amni-reports-src"
private const val LYR_REPORTS = "amni-reports-lyr"
private const val SRC_LOC = "amni-loc-src"
private const val LYR_LOC_HALO = "amni-loc-halo"
private const val LYR_LOC_DOT = "amni-loc-dot"

private fun styleBuilderFor(basemap: Int): Style.Builder = when (basemap) {
    2 -> Style.Builder().fromJson(
        """
        {
          "version": 8,
          "sources": {
            "esri-sat": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256
            }
          },
          "layers": [
            { "id": "esri-sat-layer", "type": "raster", "source": "esri-sat" }
          ]
        }
        """.trimIndent()
    )
    1 -> Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
    else -> Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")
}

private fun installOverlayLayers(style: Style, accentArgb: Int) {
    fun ensureSource(id: String) {
        if (style.getSource(id) == null) style.addSource(GeoJsonSource(id))
    }
    ensureSource(SRC_ROUTE)
    ensureSource(SRC_STOPS)
    ensureSource(SRC_DEST)
    ensureSource(SRC_REPORTS)
    ensureSource(SRC_LOC)

    if (style.getLayer(LYR_ROUTE_CASING) == null) {
        style.addLayer(
            LineLayer(LYR_ROUTE_CASING, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(0xAA000000.toInt()),
                PropertyFactory.lineWidth(11f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LYR_ROUTE_LINE) == null) {
        style.addLayer(
            LineLayer(LYR_ROUTE_LINE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(accentArgb),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
    if (style.getLayer(LYR_STOPS) == null) {
        style.addLayer(
            CircleLayer(LYR_STOPS, SRC_STOPS).withProperties(
                PropertyFactory.circleColor(accentArgb),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor(0xFF0A0B0E.toInt()),
                PropertyFactory.circleStrokeWidth(2.5f)
            )
        )
    }
    if (style.getLayer(LYR_DEST) == null) {
        style.addLayer(
            CircleLayer(LYR_DEST, SRC_DEST).withProperties(
                PropertyFactory.circleColor(accentArgb),
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleStrokeColor(0xFF0A0B0E.toInt()),
                PropertyFactory.circleStrokeWidth(3f)
            )
        )
    }
    if (style.getLayer(LYR_REPORTS) == null) {
        style.addLayer(
            CircleLayer(LYR_REPORTS, SRC_REPORTS).withProperties(
                PropertyFactory.circleColor(0xFFFFB454.toInt()),
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleStrokeColor(0xFF0A0B0E.toInt()),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
    }
    if (style.getLayer(LYR_LOC_HALO) == null) {
        style.addLayer(
            CircleLayer(LYR_LOC_HALO, SRC_LOC).withProperties(
                PropertyFactory.circleColor(accentArgb),
                PropertyFactory.circleOpacity(0.22f),
                PropertyFactory.circleRadius(18f)
            )
        )
    }
    if (style.getLayer(LYR_LOC_DOT) == null) {
        style.addLayer(
            CircleLayer(LYR_LOC_DOT, SRC_LOC).withProperties(
                PropertyFactory.circleColor(accentArgb),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor(0xFF0A0B0E.toInt()),
                PropertyFactory.circleStrokeWidth(3f)
            )
        )
    }
}

private fun syncOverlays(
    style: Style,
    route: RouteResult?,
    stops: List<RoutePoint>,
    destination: RoutePoint?,
    reports: List<Report>,
    location: Location?,
    accentArgb: Int
) {
    style.getLayerAs<LineLayer>(LYR_ROUTE_LINE)?.setProperties(PropertyFactory.lineColor(accentArgb))
    style.getLayerAs<CircleLayer>(LYR_STOPS)?.setProperties(PropertyFactory.circleColor(accentArgb))
    style.getLayerAs<CircleLayer>(LYR_DEST)?.setProperties(PropertyFactory.circleColor(accentArgb))
    style.getLayerAs<CircleLayer>(LYR_LOC_HALO)?.setProperties(PropertyFactory.circleColor(accentArgb))
    style.getLayerAs<CircleLayer>(LYR_LOC_DOT)?.setProperties(PropertyFactory.circleColor(accentArgb))

    val routeFeatures = if (route != null && route.points.size > 1) {
        listOf(Feature.fromGeometry(LineString.fromLngLats(route.points.map { Point.fromLngLat(it.lon, it.lat) })))
    } else emptyList()
    style.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))

    val stopFeatures = stops.dropLast(1).map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
    style.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(FeatureCollection.fromFeatures(stopFeatures))

    val destFeatures = destination?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyList()
    style.getSourceAs<GeoJsonSource>(SRC_DEST)?.setGeoJson(FeatureCollection.fromFeatures(destFeatures))

    val reportFeatures = reports.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }
    style.getSourceAs<GeoJsonSource>(SRC_REPORTS)?.setGeoJson(FeatureCollection.fromFeatures(reportFeatures))

    val locFeatures = location?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))) } ?: emptyList()
    style.getSourceAs<GeoJsonSource>(SRC_LOC)?.setGeoJson(FeatureCollection.fromFeatures(locFeatures))
}

@Composable
fun AmniMap(
    camera: MapCamera,
    @Suppress("UNUSED_PARAMETER") tiles: TileStore,
    route: RouteResult?,
    reports: List<Report>,
    stops: List<RoutePoint>,
    destination: RoutePoint?,
    location: Location?,
    accent: Color,
    @Suppress("UNUSED_PARAMETER") panel: Color,
    onLongPress: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveLongPress by rememberUpdatedState(onLongPress)
    val accentArgb = accent.toArgb()

    remember(ctx) { MapLibre.getInstance(ctx.applicationContext) }

    val mapView = remember(ctx) {
        org.maplibre.android.maps.MapView(
            ctx,
            MapLibreMapOptions.createFromAttributes(ctx).textureMode(true)
        ).apply {
            onCreate(null)
            onStart()
            onResume()
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var styleGen by remember { mutableIntStateOf(0) }
    var userDragging by remember { mutableStateOf(false) }
    var mapDestroyed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val obs = LifecycleEventObserver { _, event ->
            if (mapDestroyed) return@LifecycleEventObserver
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> {
                        mapDestroyed = true
                        loadedStyle = null
                        map = null
                        mapView.onDestroy()
                    }
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            if (!mapDestroyed) {
                mapDestroyed = true
                loadedStyle = null
                map = null
                mapView.post {
                    runCatching {
                        mapView.onPause()
                        mapView.onStop()
                        mapView.onDestroy()
                    }
                }
            }
        }
    }

    var lastBearing by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(location) {
        location?.let { l ->
            if (l.hasBearing() && (l.speed > 0.5f || abs(l.bearing) > 0.1f)) {
                lastBearing = l.bearing.toDouble()
            }
        }
    }
    LaunchedEffect(location, camera.follow) {
        if (camera.follow) {
            location?.let {
                camera.lat = it.latitude
                camera.lon = it.longitude
            }
        }
    }
    LaunchedEffect(map, camera.basemap, mapDestroyed) {
        val m = map ?: return@LaunchedEffect
        if (mapDestroyed) return@LaunchedEffect
        loadedStyle = null
        runCatching {
            m.setStyle(styleBuilderFor(camera.basemap)) { style ->
                if (mapDestroyed) return@setStyle
                runCatching {
                    installOverlayLayers(style, accentArgb)
                    loadedStyle = style
                    styleGen++
                }
            }
        }
    }
    LaunchedEffect(loadedStyle, styleGen, route, reports, stops, destination, location, accentArgb, mapDestroyed) {
        if (mapDestroyed) return@LaunchedEffect
        val st = loadedStyle ?: return@LaunchedEffect
        if (!st.isFullyLoaded) return@LaunchedEffect
        runCatching { syncOverlays(st, route, stops, destination, reports, location, accentArgb) }
    }
    LaunchedEffect(map, camera.lat, camera.lon, camera.zoom, camera.tilt, camera.northUp, lastBearing, location, mapDestroyed) {
        if (mapDestroyed) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (userDragging) return@LaunchedEffect
        runCatching {
            val targetBearing = if (camera.northUp) 0.0 else lastBearing
            val targetTilt = if (camera.tilt) 52.0 else 0.0
            val padTop = if (!camera.northUp && camera.tilt && mapView.height > 0) (mapView.height * 0.34).coerceAtLeast(0.0) else 0.0
            val cur = m.cameraPosition
            val curT = cur.target
            val moved = curT == null ||
                abs(curT.latitude - camera.lat) > 1e-6 ||
                abs(curT.longitude - camera.lon) > 1e-6 ||
                abs(cur.zoom - camera.zoom.toDouble()) > 0.05 ||
                abs(cur.tilt - targetTilt) > 0.5 ||
                abs(cur.bearing - targetBearing) > 0.5
            if (moved) {
                val pos = CameraPosition.Builder()
                    .target(LatLng(camera.lat, camera.lon))
                    .zoom(camera.zoom.toDouble())
                    .tilt(targetTilt)
                    .bearing(targetBearing)
                    .padding(0.0, padTop, 0.0, 0.0)
                    .build()
                m.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
            }
        }
    }

    Box(modifier) {
        AndroidView(
            factory = {
                mapView.getMapAsync { m ->
                    m.uiSettings.isCompassEnabled = false
                    m.uiSettings.isLogoEnabled = false
                    m.uiSettings.isAttributionEnabled = false
                    m.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            userDragging = true
                            camera.follow = false
                        }
                    }
                    m.addOnCameraIdleListener {
                        if (userDragging) {
                            userDragging = false
                            m.cameraPosition.target?.let { t ->
                                camera.lat = t.latitude
                                camera.lon = t.longitude
                            }
                            camera.zoom = m.cameraPosition.zoom.roundToInt().coerceIn(3, 19)
                        }
                    }
                    m.addOnMapLongClickListener { latLng ->
                        liveLongPress(latLng.latitude, latLng.longitude)
                        true
                    }
                    map = m
                }
                mapView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
