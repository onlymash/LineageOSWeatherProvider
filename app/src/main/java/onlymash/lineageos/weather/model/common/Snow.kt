package onlymash.lineageos.weather.model.common

import com.google.gson.annotations.SerializedName

data class Snow(
    @SerializedName("3h")
    val threeH: Double?
)