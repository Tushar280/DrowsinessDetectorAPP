package com.example.drowsinessdetector

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val spinnerRegion = view.findViewById<Spinner>(R.id.spinnerRegion)
        val seekThreshold = view.findViewById<SeekBar>(R.id.seekThreshold)
        val textThresholdVal = view.findViewById<TextView>(R.id.textThresholdVal)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSettings)

        val regions = arrayOf("North America", "Europe", "Asia", "South America", "Australia", "Africa")
        spinnerRegion.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, regions)

        val prefs = requireActivity().getSharedPreferences("DrowsinessPrefs", Context.MODE_PRIVATE)
        val currentThreshold = prefs.getInt("threshold", 7)
        val currentRegion = prefs.getString("region", "North America")

        seekThreshold.progress = currentThreshold
        textThresholdVal.text = currentThreshold.toString()
        val regionIndex = regions.indexOf(currentRegion)
        if(regionIndex >= 0) spinnerRegion.setSelection(regionIndex)

        seekThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var p = progress
                if (p < 2) p = 2 
                textThresholdVal.text = p.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val p = seekThreshold.progress.coerceAtLeast(2)
            prefs.edit()
                .putInt("threshold", p)
                .putString("region", spinnerRegion.selectedItem.toString())
                .apply()
            
            Toast.makeText(requireContext(), "Settings Saved Successfully", Toast.LENGTH_SHORT).show()
        }
    }
}
