package onlymash.lineageos.weather.model
import com.google.gson.annotations.SerializedName
import onlymash.lineageos.weather.model.common.*


data class ForecastWeather(
    @SerializedName("city")
    val city: City,
    @SerializedName("cnt")
    val cnt: Int,
    @SerializedName("cod")
    val cod: String,
    @SerializedName("list")
    val list: List<Data>,
    @SerializedName("message")
    val message: Double
)