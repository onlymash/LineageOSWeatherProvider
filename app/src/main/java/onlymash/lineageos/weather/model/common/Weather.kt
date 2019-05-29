package onlymash.lineageos.weather.model.common

import com.google.gson.annotations.SerializedName
import lineageos.providers.WeatherContract

data class Weather(
    @SerializedName("description")
    val description: String,
    @SerializedName("icon")
    val icon: String,
    @SerializedName("id")
    var id: Int = WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE,
    @SerializedName("main")
    val main: String
)