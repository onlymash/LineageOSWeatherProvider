package onlymash.lineageos.weather.repository

import android.location.Location
import lineageos.weather.WeatherInfo
import lineageos.weather.WeatherLocation

interface WeatherRepository {

    fun queryWeather(location: Location): WeatherInfo?

    fun queryWeather(weatherLocation: WeatherLocation): WeatherInfo?

    fun lookupCity(cityName: String): List<WeatherLocation>
}