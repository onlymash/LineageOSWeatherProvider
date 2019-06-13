package onlymash.lineageos.weather.repository

import android.location.Location
import lineageos.weather.WeatherInfo
import lineageos.weather.WeatherLocation

interface WeatherRepository {

    suspend fun queryWeather(location: Location): WeatherInfo?

    suspend fun queryWeather(weatherLocation: WeatherLocation): WeatherInfo?

    suspend fun lookupCity(cityName: String): List<WeatherLocation>
}