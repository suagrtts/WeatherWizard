package cit.edu.delosreyes.weatherwizardui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment

class CalendarFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Export to calendar button
        view.findViewById<Button>(R.id.btnExportCalendar).setOnClickListener {
            Toast.makeText(requireContext(), "Export to calendar coming soon.", Toast.LENGTH_SHORT).show()
        }
    }
}
