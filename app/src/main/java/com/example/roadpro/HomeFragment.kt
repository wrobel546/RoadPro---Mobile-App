package com.example.roadpro

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.roadpro.databinding.FragmentHomeBinding


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Odbierz adres e-mail z argumentów
        val userEmail = arguments?.getString("userEmail") ?: "example@gmail.com"
        println("Odebrano email w HomeFragment: $userEmail") // Log

        // Ustaw adres e-mail w TextView
        binding.emailEt.text = userEmail
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}