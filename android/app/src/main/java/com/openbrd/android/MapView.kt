package com.openbrd.android


import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.location.LocationManager
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

const val ZOOM_LEVEL = "com.openbrd.android.mapSettings.zoomLevel"
const val MAP_CENTER_LATITUDE = "com.openbrd.android.mapSettings.mapCenter.latitude"
const val MAP_CENTER_LONGITUDE = "com.openbrd.android.mapSettings.mapCenter.longitude"

const val IS_PIVOT_SET = "com.openbrd.android.mapSettings.isPivotSet"
const val PIVOT_X = "com.openbrd.android.mapSettings.pivotX"
const val PIVOT_Y = "com.openbrd.android.mapSettings.pivotY"
const val ROTATION = "com.openbrd.android.mapSettings.rotation"
const val SCALE_X = "com.openbrd.android.mapSettings.scaleX"
const val SCALE_Y = "com.openbrd.android.mapSettings.scaleY"

const val IS_LAST_LOCATION_SET = "com.openbrd.android.mapSettings.isLastLocationSet"
const val LAST_LOCATION_LATITUDE = "com.openbrd.android.mapSettings.lastLocation.latitude"
const val LAST_LOCATION_LONGITUDE = "com.openbrd.android.mapSettings.lastLocation.longitude"
const val MAP_ORIENTATION = "com.openbrd.android.mapSettings.mapOrientation"


fun defaultOnLoad(map:MapView){
    // set default settings
    map.minZoomLevel = 5.0
    map.controller.setZoom(7.0)
    map.isVerticalMapRepetitionEnabled = false
    map.isHorizontalMapRepetitionEnabled = false

    // load last session's settings
    val sp = PreferenceManager.getDefaultSharedPreferences(map.context)

    val zoomLevel = sp.getDouble(ZOOM_LEVEL,0.0)
    if(zoomLevel!=0.0) map.controller.setZoom(zoomLevel)

    map.controller.setCenter(GeoPoint(
        sp.getDouble(MAP_CENTER_LATITUDE,0.0),
        sp.getDouble(MAP_CENTER_LONGITUDE,0.0)
    ))

    val isLastLocationSet = sp.getBoolean(IS_LAST_LOCATION_SET,false)
    val lastLocation = Pair(
        sp.getDouble(LAST_LOCATION_LATITUDE, 0.0),
        sp.getDouble(LAST_LOCATION_LONGITUDE, 0.0)
    )

    map.mapOrientation = sp.getFloat(MAP_ORIENTATION, 0.0F)

    if(sp.getBoolean(IS_PIVOT_SET,false)){
        map.pivotX = sp.getFloat(PIVOT_X,0.0F)
        map.pivotY = sp.getFloat(PIVOT_Y,0.0F)
        map.rotation = sp.getFloat(ROTATION,0.0F)
        map.scaleX = sp.getFloat(SCALE_X,0.0F)
        map.scaleY = sp.getFloat(SCALE_Y, 0.0F)
    }

    // enable all overlays
    val mReceive: MapEventsReceiver = object : MapEventsReceiver {
        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
            // TODO: marker create an activity and save it to db
//            Toast.makeText(
//                map.context,
//                p.latitude.toString() + " - " + p.longitude,
//                Toast.LENGTH_LONG
//            ).show()
            return true
        }
        override fun longPressHelper(p: GeoPoint): Boolean {
            return false
        }
    }
    val overlayEvents = MapEventsOverlay(mReceive)
    map.overlays.add(overlayEvents)

    val gpsProvider = GpsMyLocationProvider(map.context)
    gpsProvider.addLocationSource(LocationManager.NETWORK_PROVIDER)
    gpsProvider.addLocationSource(LocationManager.GPS_PROVIDER)
    val mLocationOverlay = object: MyLocationNewOverlay(gpsProvider, map) {
        // TODO: not working
        override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
            val reuse = Point()
            e?.let { mapView?.projection?.rotateAndScalePoint(e.x.toInt(), e.y.toInt(), reuse) }
            if (reuse.x < mDirectionArrowCenterX * 2 && reuse.y < mDirectionArrowCenterY * 2) {
                e?.let { map.controller.animateTo(it.x.toInt(), it.y.toInt()) }
                Log.d("LOCATION_PRESS",e?.x.toString() + ";" + e?.y.toString())
                return true
            }
            return super.onSingleTapConfirmed(e, mapView)
        }
    }
    mLocationOverlay.enableFollowLocation()
    mLocationOverlay.enableMyLocation()

    val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    fun runOnUiThread(function: suspend () -> Unit) = uiScope.launch { function() }
    mLocationOverlay.runOnFirstFix{runOnUiThread {
        map.isEnabled = false
        map.controller.animateTo(mLocationOverlay.myLocation)
        map.isEnabled = true
    }}
    map.overlays.add(mLocationOverlay)

    val mapNorthCompassOverlay = object: CompassOverlay(map.context, map) {
        override fun draw(c: Canvas?, pProjection: Projection?) {
            drawCompass(c, -map.mapOrientation, pProjection?.screenRect)
        }
        override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
            val reuse = Point()
            e?.let { mapView?.projection?.rotateAndScalePoint(e.x.toInt(), e.y.toInt(), reuse) }
            if (reuse.x < mCompassFrameCenterX * 2 && reuse.y < mCompassFrameCenterY * 2) {
                map.mapOrientation = 0.0F
                Log.d("COMPASS_PRESS",e?.x.toString() + ";" + e?.y.toString())
                Log.d("COMPASS_PRESS",reuse.x.toString() + ";" + reuse.y.toString())
                Log.d("COMPASS_PRESS",mCompassFrameCenterX.toString() + ";" + mCompassFrameCenterY.toString())
                return true
            }
            return super.onSingleTapConfirmed(e, mapView)
        }
    }
    mapNorthCompassOverlay.enableCompass()
    map.overlays.add(mapNorthCompassOverlay)
//    val compassOverlay = CompassOverlay(map.context, InternalCompassOrientationProvider(map.context), map)
//    compassOverlay.enableCompass()
//    map.overlays.add(compassOverlay)

    val rotationGestureOverlay = RotationGestureOverlay(map)
    rotationGestureOverlay.isEnabled
    map.setMultiTouchControls(true)
    map.overlays.add(rotationGestureOverlay)
}


/**
 * A composable Google Map.
 * @author Arnau Mora
 * @since 20211230
 * @param modifier Modifiers to apply to the map.
 * @param onLoad This will get called once the map has been loaded.
 */
@Composable
fun MapView(
    modifier: Modifier = Modifier,
    onLoad: ((map: MapView) -> Unit)? = null
) {
    val mapViewState = rememberMapViewWithLifecycle()
    var isLoaded by remember {mutableStateOf(false)}

    AndroidView(
        { mapViewState },
        modifier
    ) { mapView ->
        if(!isLoaded){
            defaultOnLoad(mapView)
            isLoaded = true
        }
        onLoad?.invoke(mapView)
    }
}