package onlymash.lineageos.weather.model
import com.google.gson.annotations.SerializedName
import onlymash.lineageos.weather.model.common.*


data class CityResponse(
    @SerializedName("cod")
    val cod: String,
    @SerializedName("count")
    val count: Int,
    @SerializedName("list")
    val list: List<Data>,
    @SerializedName("message")
    val message: String
)
