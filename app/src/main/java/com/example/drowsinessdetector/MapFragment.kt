package com.example.drowsinessdetector

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        
        mapView.getMapAsync { map ->
            val apiKey = getString(R.string.geoapify_key)
            // Use Geoapify's beautiful OSM Carto vector style
            val styleUrl = "https://maps.geoapify.com/v1/styles/osm-carto/style.json?apiKey=$apiKey"
            map.setStyle(styleUrl) { style ->
                // Center Map default
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(40.7128, -74.0060)) // Default to a standard city center
                    .zoom(10.0)
                    .build()
            }
        }
        
        view.findViewById<Button>(R.id.btnStartCameraTracking).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, DetectionFragment())
                .commit()
            
            // Highlight nav drawer explicitly on transit
            val navigationView = requireActivity().findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView)
            navigationView.setCheckedItem(R.id.nav_detection)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }
}
