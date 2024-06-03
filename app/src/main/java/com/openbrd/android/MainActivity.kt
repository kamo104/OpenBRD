package com.openbrd.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.openbrd.android.ui.theme.OpenBrdTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.DateFormat


class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenBrdTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Main()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main(){
    val context = LocalContext.current.applicationContext
    val dbHandle = AppDatabase.getDatabase(context)

    val multiplePermissionsState = rememberMultiplePermissionsState(listOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        ))

    if(!multiplePermissionsState.allPermissionsGranted){
        SideEffect {
            multiplePermissionsState.launchMultiplePermissionRequest()
        }
    }else{
        // start the service

        if(!LocationBTService.isRunning){
            val serviceIntent = Intent(context, LocationBTService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        val selectionState = rememberMultiSelectionState()

        val paths by dbHandle.pathDao().getFlow().collectAsState(initial = emptyList())
        val selectedPaths = remember { mutableStateListOf<Path>() }

        var map:MapView? = remember { null}

        val drawerCloseOpen = fun(){
            scope.launch {
                drawerState.apply {
                    if (isClosed) open() else close()
                }
            }
        }
        val clearPaths = fun(){
            for (overlay in map?.overlays!!){
                when (overlay){
                    is Polyline ->{
                        map!!.overlayManager.remove(overlay)
                    }
                }
            }
        }
        val drawPaths = fun(it:Path){
            for(path in constructPaths(LocationSnapshot.loadLocations(it.path),Event.loadEvents(it.events))){
                val pathOverlay = Polyline(map)
                for(point in path){
                    pathOverlay.addPoint(point)
                }
                map?.overlays!!.add(pathOverlay)
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet {
                    Column(horizontalAlignment=Alignment.End) {
                        Row {
                            AnimatedVisibility(
                                visible = selectionState.isMultiSelectionModeEnabled,
                                enter = slideInHorizontally(initialOffsetX = { -it / 2 }) + fadeIn(),
                                exit = slideOutHorizontally(targetOffsetX = { -it / 2 }) + fadeOut()
                            ) {
                                Row {
                                    // button merge
//                                    Button(onClick = {
//
//                                    }) {
//                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "")
//                                    }
                                    // button trash
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                context?.let {
                                                    dbHandle.pathDao().deleteAll(*selectedPaths.toTypedArray())
                                                    selectedPaths.clear()
                                                }
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "")
                                    }
                                }
                            }
                            // button done
                            Button(onClick = onClick@{
                                if(selectionState.isMultiSelectionModeEnabled){
                                    selectionState.isMultiSelectionModeEnabled = false
                                    // draw every selected path
                                    clearPaths()
                                    for(path in selectedPaths){
                                        drawPaths(path)
                                    }
                                }
                                drawerCloseOpen()
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = "")
                            }
                        }
                        MultiSelectionList(
                            items = paths,
                            selectedItems = selectedPaths,
                            itemContent = {
                                val events = Event.loadEvents(it.events)
                                val start = events.first().date
//                                val stopEvent = events.last().date

                                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                                val formattedDate = dateFormat.format(start)

                                Row{
                                    Text(
                                        text = formattedDate,
                                        textAlign= TextAlign.Start,
                                        modifier = Modifier.wrapContentWidth()
                                    )
                                }
                            },
                            onClick = {
                                clearPaths()
                                drawPaths(it)
                                drawerCloseOpen()
                            },
                            state = selectionState
                        )
                    }
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MapView(Modifier.fillMaxSize(),onLoad = {map = it})
                FloatingActionButton(
                    onClick = {
                        drawerCloseOpen()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Filled.List, contentDescription = "Show routes")
                }
            }
        }
    }
}