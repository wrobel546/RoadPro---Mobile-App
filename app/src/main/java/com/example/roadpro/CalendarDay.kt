package com.example.roadpro

data class CalendarDay(
    val date: String,
    val eventCount: Int,
    val isSelected: Boolean = false // nowa flaga
)
