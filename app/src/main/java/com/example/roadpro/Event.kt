package com.example.roadpro

//data class Fee(
//    val name: String = "",
//    val amount: Double = 0.0
//)

data class Event(
    val name: String = "",
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val color: Int = 0,
    var done: Int = 0, // 0 - nie zrealizowana, 1 - zrealizowana
    val StartLicznik: Long = 0,
    val KoniecLicznik: Long = 0,
    val fees: List<Fee> = emptyList(),
    var payment: Double = 0.0
)