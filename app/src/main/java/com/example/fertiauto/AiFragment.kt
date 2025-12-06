package com.example.fertiauto

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class AiFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment_ai layout (make sure res/layout/fragment_ai.xml exists)
        return inflater.inflate(R.layout.fragment_ai, container, false)
    }
}