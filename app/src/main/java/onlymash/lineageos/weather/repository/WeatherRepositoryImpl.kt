package onlymash.lineageos.weather.repository

import android.content.Context
import android.location.Location
import lineageos.weather.WeatherInfo
import lineageos.weather.WeatherLocation
import onlymash.lineageos.weather.api.OpenWeatherMapApi
import android.text.TextUtils
import lineageos.providers.WeatherContract
import lineageos.providers.LineageSettings
import onlymash.lineageos.weather.model.CurrentWeather
import onlymash.lineageos.weather.model.ForecastWeather
import java.util.*
import kotlin.collections.ArrayList

private const val METRIC_UNITS = "metric"
private const val IMPERIAL_UNITS = "imperial"
private const val MPS_TO_KPH = 3.6
private const val FORECAST_ITEMS_PER_DAY = 8
private const val SEARCH_CITY_TYPE = "like"

private val SUPPORTED_LANGUAGES: MutableSet<String> = hashSetOf(
    "en",
    "ru", //Russian
    "it", //Italian
    "es", //Spanish
    "sp", //Spanish
    "uk", //Ukrainian
    "ua", //Ukrainian
    "de", //German
    "pt", //Portuguese
    "ro", //Romanian
    "pl", //Polish
    "fi", //Finnish
    "nl", //Dutch
    "fr", //French
    "bg", //Bulgarian
    "sv", //Swedish
    "se", //Swedish
    "zh_tw", //Chinese Traditional
    "zh_cn", //Chinese Simplified
    "tr", //Turkish
    "hr", //Croatian
    "ca" //Catalan
)

private val ICON_MAPPING: MutableMap<String, Int> = mutableMapOf(
    Pair("01d", WeatherContract.WeatherColumns.WeatherCode.SUNNY),
    Pair("01n", WeatherContract.WeatherColumns.WeatherCode.CLEAR_NIGHT),
    Pair("02d", WeatherContract.WeatherColumns.WeatherCode.PARTLY_CLOUDY_DAY),
    Pair("02n", WeatherContract.WeatherColumns.WeatherCode.PARTLY_CLOUDY_NIGHT),
    Pair("03d", WeatherContract.WeatherColumns.WeatherCode.CLOUDY),
    Pair("03n", WeatherContract.WeatherColumns.WeatherCode.CLOUDY),
    Pair("04d", WeatherContract.WeatherColumns.WeatherCode.MOSTLY_CLOUDY_DAY),
    Pair("04n", WeatherContract.WeatherColumns.WeatherCode.MOSTLY_CLOUDY_NIGHT),
    Pair("09d", WeatherContract.WeatherColumns.WeatherCode.SHOWERS),
    Pair("09n", WeatherContract.WeatherColumns.WeatherCode.SHOWERS),
    Pair("10d", WeatherContract.WeatherColumns.WeatherCode.SCATTERED_SHOWERS),
    Pair("10n", WeatherContract.WeatherColumns.WeatherCode.THUNDERSHOWER),
    Pair("11d", WeatherContract.WeatherColumns.WeatherCode.THUNDERSTORMS),
    Pair("11n", WeatherContract.WeatherColumns.WeatherCode.THUNDERSTORMS),
    Pair("13d", WeatherContract.WeatherColumns.WeatherCode.SNOW),
    Pair("13n", WeatherContract.WeatherColumns.WeatherCode.SNOW),
    Pair("50d", WeatherContract.WeatherColumns.WeatherCode.HAZE),
    Pair("50n", WeatherContract.WeatherColumns.WeatherCode.FOGGY)
)

class InvalidApiKeyException : Exception("A valid API key is required to process the request")

class WeatherRepositoryImpl(
    private val context: Context,
    private val api: OpenWeatherMapApi
) : WeatherRepository {

    var apiKey: String? = null

    @Throws(InvalidApiKeyException::class)
    override fun queryWeather(location: Location): WeatherInfo? {
        val key = apiKey
        if (key.isNullOrEmpty()) throw InvalidApiKeyException()
        val languageCode = getLanguageCode()
        val tempUnit = getTempUnitFromSettings()
        val units = mapTempUnit(tempUnit)
        val response = try {
            api.queryCurrentWeather(
                lat = location.latitude,
                lon = location.longitude,
                units = units,
                lang = languageCode,
                appId = key).execute()
        } catch (_: Exception) {
            null
        } ?: return null
        return if (response.isSuccessful) {
            var forecastWeather: ForecastWeather? = null
            try {
                val forecastResponse = api.queryForecastWeather(
                    lat = location.latitude,
                    lon = location.longitude,
                    units = units,
                    lang = languageCode,
                    appId = key
                ).execute()
                if (forecastResponse.isSuccessful) {
                    forecastWeather = forecastResponse.body()
                }
            } catch (_: Exception) { }
            processWeather(response.body(), forecastWeather, tempUnit)
        } else null
    }

    @Throws(InvalidApiKeyException::class)
    override fun queryWeather(weatherLocation: WeatherLocation): WeatherInfo? {
        val key = apiKey
        if (key.isNullOrEmpty()) throw InvalidApiKeyException()
        val languageCode = getLanguageCode()
        val tempUnit = getTempUnitFromSettings()
        val units = mapTempUnit(tempUnit)
        val response = try {
            api.queryCurrentWeather(
                cityId = weatherLocation.cityId,
                units = units,
                lang = languageCode,
                appId = key).execute()
        } catch (_: Exception) {
            null
        } ?: return null
        return if (response.isSuccessful) {
            var forecastWeather: ForecastWeather? = null
            try {
                val forecastResponse = api.queryForecastWeather(
                    cityId = weatherLocation.cityId,
                    units = units,
                    lang = languageCode,
                    appId = key
                ).execute()
                if (forecastResponse.isSuccessful) {
                    forecastWeather = forecastResponse.body()
                }
            } catch (_: Exception) { }
            processWeather(response.body(), forecastWeather, tempUnit)
        } else null
    }

    @Throws(InvalidApiKeyException::class)
    override fun lookupCity(cityName: String): List<WeatherLocation> {
        val key = apiKey
        if (key.isNullOrEmpty()) throw InvalidApiKeyException()
        val response = try {
            api.lookupCityWeather(cityName, getLanguageCode(), SEARCH_CITY_TYPE, key).execute()
        } catch (_: Exception) {
            null
        }
        if (response != null && response.isSuccessful) {
            val data = response.body()?.list ?: return emptyList()
            val weatherLocations = arrayListOf<WeatherLocation>()
            data.forEach {
                weatherLocations.add(
                    WeatherLocation.Builder(it.id.toString(), it.name)
                        .setCountry(it.sys.country ?: "").build())
            }
            return weatherLocations
        } else {
            return emptyList()
        }
    }


    private fun processWeather(
        currentWeather: CurrentWeather?,
        forecastWeather: ForecastWeather?,
        tempUnit: Int): WeatherInfo? {
        if (currentWeather == null || currentWeather.cod == 404) {
            return null
        }
        val cityName = currentWeather.name
        val temperature = currentWeather.main.temp
        val builder = WeatherInfo.Builder(
            cityName,
            sanitizeTemperature(temperature),
            tempUnit)
        val weathers = currentWeather.weather
        val condition = if (weathers.isEmpty()) {
            mapConditionIconToCode("", WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE)
        } else {
            mapConditionIconToCode(weathers[0].icon, weathers[0].id)
        }
        with(currentWeather) {
            builder.apply {
                setWeatherCondition(condition)
                setHumidity(main.humidity)
                setTodaysHigh(main.tempMax)
                setTodaysLow(main.tempMin)
                setWind(wind.speed,
                    if (tempUnit == WeatherContract.WeatherColumns.TempUnit.CELSIUS) {
                        wind.deg * MPS_TO_KPH
                    } else {
                        wind.deg
                    },
                    WeatherContract.WeatherColumns.WindSpeedUnit.KPH)
            }
        }
        if (forecastWeather == null) return builder.build()
        var maxItems = forecastWeather.list.size
        val forecastList = ArrayList<WeatherInfo.DayForecast>()
        var forecastBuilder: WeatherInfo.DayForecast.Builder? = null
        var min = 0.0
        var max = 0.0
        forecastWeather.list.forEachIndexed { index, data ->
            val forecastCalendar = Calendar.getInstance()
            forecastCalendar.timeInMillis = data.dt * 1000
            if (index == 0) {
                val forecastDay = forecastCalendar.get(Calendar.DAY_OF_YEAR)
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (currentDay != forecastDay) {
                    forecastBuilder = WeatherInfo.DayForecast.Builder(condition).apply {
                        setHigh(currentWeather.main.tempMax)
                        setLow(currentWeather.main.tempMin)
                    }
                    forecastList.add(forecastBuilder!!.build())
                    maxItems -= FORECAST_ITEMS_PER_DAY
                }
                max = data.main.tempMax
                min = data.main.tempMin
            }
            if (data.main.tempMax > max) {
                max = data.main.tempMax
            }
            if (data.main.tempMin < min) {
                min = data.main.tempMin
            }
            if (index % FORECAST_ITEMS_PER_DAY == 0) {
                forecastBuilder =
                    if (data.weather.isEmpty()) {
                        WeatherInfo.DayForecast.Builder(mapConditionIconToCode(
                            "", WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE))
                    } else {
                        WeatherInfo.DayForecast.Builder(mapConditionIconToCode(
                            data.weather[0].icon, data.weather[0].id))
                    }
            }
            val forecastHour = forecastCalendar.get(Calendar.HOUR_OF_DAY)
            if (forecastHour > 21) {
                forecastBuilder?.apply {
                    setHigh(max)
                    setLow(min)
                    forecastList.add(build())
                }
            }
        }
        builder.setForecast(forecastList)
        return builder.build()
    }


    // OpenWeatherMap sometimes returns temperatures in Kelvin even if we ask it
    // for deg C or deg F. Detect this and convert accordingly.
    private fun sanitizeTemperature(value: Double, metric: Boolean = true): Double {
        var tmp = value
        // threshold chosen to work for both C and F. 170 deg F is hotter
        // than the hottest place on earth.
        if (tmp > 170.0) {
            // K -> deg C
            tmp -= 273.15
            if (!metric) {
                // deg C -> deg F
                tmp = tmp * 1.8 + 32.0
            }
        }
        return tmp
    }

    private fun getLanguageCode(): String {
        val locale = context.resources.configuration.locales[0]
        var selector = locale.language

        //Special cases
        if (TextUtils.equals(selector, "zh")) {
            selector += "_" + locale.country
        }

        return if (SUPPORTED_LANGUAGES.contains(selector)) {
            selector
        } else {
            //Default to english
            "en"
        }
    }

    private fun getTempUnitFromSettings(): Int {
        return try {
            LineageSettings.Global.getInt(
                context.contentResolver,
                LineageSettings.Global.WEATHER_TEMPERATURE_UNIT
            )
        } catch (_: Exception) {
            //Default to metric
            WeatherContract.WeatherColumns.TempUnit.CELSIUS
        }
    }

    private fun mapTempUnit(tempUnit: Int): String {
        return when (tempUnit) {
            WeatherContract.WeatherColumns.TempUnit.FAHRENHEIT -> IMPERIAL_UNITS
            WeatherContract.WeatherColumns.TempUnit.CELSIUS -> METRIC_UNITS
            else ->
                //In the unlikely case we receive an unknown temp unit, return empty string
                //to avoid sending an invalid argument in the request
                ""
        }
    }

    private fun mapConditionIconToCode(icon: String, conditionId: Int): Int {

        return when (conditionId) {
            202,
            232,
            211 -> WeatherContract.WeatherColumns.WeatherCode.THUNDERSTORMS
            
            212 -> WeatherContract.WeatherColumns.WeatherCode.HURRICANE
            221,
            231,
            201 -> WeatherContract.WeatherColumns.WeatherCode.SCATTERED_THUNDERSTORMS
            
            230, 
            200, 
            210 -> WeatherContract.WeatherColumns.WeatherCode.ISOLATED_THUNDERSTORMS

            300, 
            301, 
            302, 
            310, 
            311, 
            312, 
            313, 
            314, 
            321 -> WeatherContract.WeatherColumns.WeatherCode.DRIZZLE
            
            500, 
            501, 
            520, 
            521, 
            531, 
            502, 
            503, 
            504, 
            522 -> WeatherContract.WeatherColumns.WeatherCode.SHOWERS

            511
            -> WeatherContract.WeatherColumns.WeatherCode.FREEZING_RAIN

            600,
            620 -> WeatherContract.WeatherColumns.WeatherCode.LIGHT_SNOW_SHOWERS

            601,
            621 -> WeatherContract.WeatherColumns.WeatherCode.SNOW

            602,
            622 -> WeatherContract.WeatherColumns.WeatherCode.HEAVY_SNOW

            611,
            612 -> WeatherContract.WeatherColumns.WeatherCode.SLEET

            615,
            616 -> WeatherContract.WeatherColumns.WeatherCode.MIXED_RAIN_AND_SNOW

            741 -> WeatherContract.WeatherColumns.WeatherCode.FOGGY

            711,
            762 -> WeatherContract.WeatherColumns.WeatherCode.SMOKY

            701,
            721 -> WeatherContract.WeatherColumns.WeatherCode.HAZE

            731,
            751,
            761 -> WeatherContract.WeatherColumns.WeatherCode.DUST

            771 -> WeatherContract.WeatherColumns.WeatherCode.BLUSTERY

            781 -> WeatherContract.WeatherColumns.WeatherCode.TORNADO

            900 -> WeatherContract.WeatherColumns.WeatherCode.TORNADO

            901 -> WeatherContract.WeatherColumns.WeatherCode.TROPICAL_STORM

            902 -> WeatherContract.WeatherColumns.WeatherCode.HURRICANE

            903 -> WeatherContract.WeatherColumns.WeatherCode.COLD

            904 -> WeatherContract.WeatherColumns.WeatherCode.HOT

            905 -> WeatherContract.WeatherColumns.WeatherCode.WINDY

            906 -> WeatherContract.WeatherColumns.WeatherCode.HAIL

            else -> ICON_MAPPING[icon] ?: WeatherContract.WeatherColumns.WeatherCode.NOT_AVAILABLE
        }
    }
}