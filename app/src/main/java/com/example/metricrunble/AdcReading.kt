package com.example.metricrunble

// Definición de la clase AdcReading
data class AdcReading(
    val apint: Int,
    val apext: Int,
    val talon: Int,
    val adc4: Int,
    val macAddress: String,
    val timeStamp: String// Agrega un nombre adecuado para este campo
    //val averageApint: Double,
    //val averageApext: Double
)

