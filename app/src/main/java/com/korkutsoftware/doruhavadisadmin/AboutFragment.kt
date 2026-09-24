package com.korkutsoftware.doruhavadisadmin

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class AboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_about, container, false)

        val tvVersion = view.findViewById<TextView>(R.id.tv_about_version)
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.versionName
            tvVersion.text = "Sürüm: $version"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return view
    }
}