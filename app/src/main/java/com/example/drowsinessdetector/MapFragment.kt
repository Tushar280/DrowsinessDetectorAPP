package com.example.drowsinessdetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    
    private lateinit var etStart: EditText
    private lateinit var etEnd: EditText
    private lateinit var btnCalculate: Button
    private lateinit var btnStartTrip: Button

    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocationSilent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        etStart = view.findViewById(R.id.etStart)
        etEnd = view.findViewById(R.id.etEnd)
        btnCalculate = view.findViewById(R.id.btnCalculate)
        btnStartTrip = view.findViewById(R.id.btnStartTrip)

        mapView.getMapAsync { map ->
            mapLibreMap = map
            val apiKey = getString(R.string.geoapify_key)
            val styleUrl = "https://maps.geoapify.com/v1/styles/osm-carto/style.json?apiKey=$apiKey"
            map.setStyle(styleUrl) { style ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(40.7128, -74.0060)) 
                    .zoom(4.0)
                    .build()
                    
                fetchLocationSilent() // Immediately seek internal GPS context!
            }
        }
        
        btnCalculate.setOnClickListener {
            val startLoc = etStart.text.toString()
            val endLoc = etEnd.text.toString()

            if (endLoc.isBlank()) {
                Toast.makeText(requireContext(), "Please enter destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnCalculate.text = "CALCULATING..."
            btnCalculate.isEnabled = false

            Thread {
                performRoutingLogic(startLoc, endLoc)
            }.start()
        }
        
        btnStartTrip.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DetectionFragment())
                .commit()
            
            val navigationView = requireActivity().findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
            navigationView.setCheckedItem(R.id.nav_detection)
        }
    }

    private fun fetchLocationSilent() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        currentLocation = bestLocation
        if (currentLocation != null && mapLibreMap != null) {
            activity?.runOnUiThread {
                try {
                    mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLocation!!.latitude, currentLocation!!.longitude), 12.0))
                } catch(e: Exception) {}
            }
        }
    }

    private fun performRoutingLogic(startLoc: String, endLoc: String) {
        val apiKey = getString(R.string.geoapify_key)
        try {
            var startLat = 0.0
            var startLon = 0.0

            if (startLoc.isBlank() || startLoc.equals("Current Location", true)) {
                if (currentLocation == null) fetchLocationSilent()
                if (currentLocation == null) {
                    throw Exception("GPS Location Unavailable, please type a city!")
                }
                startLat = currentLocation!!.latitude
                startLon = currentLocation!!.longitude
            } else {
                val sCoords = geocode(startLoc, apiKey) ?: throw Exception("Start location not found")
                startLat = sCoords.first
                startLon = sCoords.second
            }

            val eCoords = geocode(endLoc, apiKey) ?: throw Exception("Destination not found")
            val endLat = eCoords.first
            val endLon = eCoords.second

            val geoJsonResult = fetchRoute(startLat, startLon, endLat, endLon, apiKey)
                ?: throw Exception("Could not map route")

            activity?.runOnUiThread {
                drawRouteOnMap(geoJsonResult)
                btnCalculate.text = "ROUTE PLANNED!"
                btnCalculate.isEnabled = true
                btnStartTrip.text = "START TRIP"
                btnStartTrip.setBackgroundColor(Color.parseColor("#4CAF50"))
                btnStartTrip.setTextColor(Color.WHITE)
            }

        } catch (e: Exception) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnCalculate.text = "FIND ROUTE"
                btnCalculate.isEnabled = true
            }
        }
    }

    private fun geocode(address: String, apiKey: String): Pair<Double, Double>? {
        try {
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            val urlString = "https://api.geoapify.com/v1/geocode/search?text=$encodedAddress&apiKey=$apiKey"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val features = json.getJSONArray("features")
                if (features.length() > 0) {
                    val coordinates = features.getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates")
                    val lon = coordinates.getDouble(0)
                    val lat = coordinates.getDouble(1)
                    return Pair(lat, lon)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun fetchRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double, apiKey: String): String? {
        try {
            // Geoapify routing endpoints: lat,lon|lat,lon
            val urlString = "https://api.geoapify.com/v1/routing?waypoints=$startLat,$startLon|$endLat,$endLon&mode=drive&apiKey=$apiKey"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun drawRouteOnMap(geoJsonString: String) {
        mapLibreMap?.style?.let { style ->
            val sourceId = "route-source"
            val layerId = "route-layer"

            var source = style.getSourceAs<GeoJsonSource>(sourceId)
            if (source == null) {
                source = GeoJsonSource(sourceId, geoJsonString)
                style.addSource(source)
            } else {
                source.setGeoJson(geoJsonString)
            }

            if (style.getLayer(layerId) == null) {
                val lineLayer = LineLayer(layerId, sourceId)
                lineLayer.setProperties(
                    PropertyFactory.lineColor(Color.parseColor("#448AFF")),
                    PropertyFactory.lineWidth(6f)
                )
                style.addLayer(lineLayer)
            }

            try {
                val json = JSONObject(geoJsonString)
                val bbox = json.getJSONArray("features").getJSONObject(0).getJSONArray("bbox")
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(bbox.getDouble(1), bbox.getDouble(0)))
                    .include(LatLng(bbox.getDouble(3), bbox.getDouble(2)))
                    .build()

                mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (e: Exception) {}
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView.onDestroy() }
}
