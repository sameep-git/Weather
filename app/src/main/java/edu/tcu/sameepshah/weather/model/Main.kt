package edu.tcu.sameepshah.weather.model

data class Main(
    val temp: Double,
    val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    val tempMin: Double,
    val tempMax: Double,
    val seaLevel: Int,
    val groundLevel: Int
)