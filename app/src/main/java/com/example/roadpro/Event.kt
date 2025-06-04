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
    var done: Int? = null,
    var payment: Double = 0.0,
    val userId: String? = null,
    val fees: List<Fee>? = null,
    val StartLicznik: Long = 0L, // domyślnie 0 zamiast null
    val KoniecLicznik: Long = 0L, // domyślnie 0 zamiast null
    val phoneNumber: String? = null // <-- dodaj to pole
) {
    constructor() : this("", "", "", "", 0, null, 0.0, null, null, 0L, 0L, null)
}