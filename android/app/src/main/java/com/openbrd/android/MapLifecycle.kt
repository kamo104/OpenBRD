package com.openbrd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current.applicationContext
    val mapView = remember {
        MapView(context).apply {
            id = R.id.map
        }
    }

    // Makes MapView follow the lifecycle of this composable
    val lifecycleObserver = rememberMapLifecycleObserver(mapView)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return mapView
}

@Composable
fun rememberMapLifecycleObserver(mapView: MapView): LifecycleEventObserver {
    val context = LocalContext.current.applicationContext
    val obs = remember(mapView) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                }
                Lifecycle.Event.ON_CREATE -> {
                    Configuration.getInstance().load(
                        context,
                        PreferenceManager.getDefaultSharedPreferences(context)
                    )
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Configuration.getInstance().save(
                        context,
                        PreferenceManager.getDefaultSharedPreferences(context)
                    )
                    val spEdit = PreferenceManager.getDefaultSharedPreferences(context).edit()

                    spEdit.putDouble(ZOOM_LEVEL,mapView.zoomLevelDouble)
                    spEdit.putDouble(MAP_CENTER_LATITUDE,mapView.mapCenter.latitude)
                    spEdit.putDouble(MAP_CENTER_LONGITUDE,mapView.mapCenter.longitude)
                    spEdit.putBoolean(IS_PIVOT_SET,mapView.isPivotSet)
                    spEdit.putFloat(PIVOT_X,mapView.pivotX)
                    spEdit.putFloat(PIVOT_Y,mapView.pivotY)
                    spEdit.putFloat(ROTATION,mapView.rotation)
                    spEdit.putFloat(SCALE_X,mapView.scaleX)
                    spEdit.putFloat(SCALE_Y,mapView.scaleY)
                    spEdit.putFloat(MAP_ORIENTATION,mapView.mapOrientation)

                    for (overlay in mapView.overlays){
                        when (overlay){
                            is MyLocationNewOverlay ->{
                                if(overlay.myLocation == null){
                                    spEdit.putBoolean(IS_LAST_LOCATION_SET,false)
                                    continue;
                                }
                                spEdit.putBoolean(IS_LAST_LOCATION_SET,true)
                                spEdit.putDouble(LAST_LOCATION_LATITUDE, overlay.myLocation.latitude)
                                spEdit.putDouble(LAST_LOCATION_LONGITUDE, overlay.myLocation.latitude)
                            }
                        }
                    }
                    spEdit.apply()
                    mapView.onDetach()
                }
                else -> {}
            }
        }
    }
    return obs
}