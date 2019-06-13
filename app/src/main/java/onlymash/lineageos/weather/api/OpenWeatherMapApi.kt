package onlymash.lineageos.weather.api

import okhttp3.OkHttpClient
import onlymash.lineageos.weather.model.CurrentWeather
import onlymash.lineageos.weather.model.CityResponse
import onlymash.lineageos.weather.model.ForecastWeather
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherMapApi {

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org"
        operator fun invoke(): OpenWeatherMapApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenWeatherMapApi::class.java)
        }
    }

    @GET("/data/2.5/weather")
    suspend fun queryCurrentWeather(
        @Query("id") cityId: String,
        @Query("units") units: String,
        @Query("lang") lang: String,
        @Query("appid") appId: String
    ): Response<CurrentWeather>

    @GET("/data/2.5/weather")
    suspend fun queryCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String,
        @Query("lang") lang: String,
        @Query("appid") appId: String
    ): Response<CurrentWeather>

    @GET("/data/2.5/forecast")
    suspend fun queryForecastWeather(
        @Query("id") cityId: String,
        @Query("units") units: String,
        @Query("lang") lang: String,
        @Query("appid") appId: String
    ): Response<ForecastWeather>

    @GET("/data/2.5/forecast")
    suspend fun queryForecastWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String,
        @Query("lang") lang: String,
        @Query("appid") appId: String
    ): Response<ForecastWeather>

    @GET("/data/2.5/find")
    suspend fun lookupCityWeather(
        @Query("q") cityName: String,
        @Query("lang") lang: String,
        @Query("type") searchType: String,
        @Query("appid") appId: String
    ): Response<CityResponse>
}
