package onlymash.lineageos.weather

import android.content.SharedPreferences
import android.location.Location
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import lineageos.weather.RequestInfo
import lineageos.weather.WeatherLocation
import lineageos.weatherservice.ServiceRequest
import lineageos.weatherservice.ServiceRequestResult
import lineageos.weatherservice.WeatherProviderService
import onlymash.lineageos.weather.api.OpenWeatherMapApi
import onlymash.lineageos.weather.repository.InvalidApiKeyException
import onlymash.lineageos.weather.repository.WeatherRepositoryImpl
import kotlin.coroutines.CoroutineContext
import androidx.preference.PreferenceManager
import lineageos.weather.LineageWeatherManager


const val API_KEY_INVALID = 0
const val API_KEY_VERIFIED = 2

private const val LOCATION_DISTANCE_METERS_THRESHOLD = 5f * 1000f
private const val REQUEST_THRESHOLD = 1000L * 60L * 10L

class WeatherMapProviderService : WeatherProviderService(),
    CoroutineScope, SharedPreferences.OnSharedPreferenceChangeListener {

    private val sp by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val api by lazy { OpenWeatherMapApi() }
    private val repo by lazy { WeatherRepositoryImpl(this, api) }

    private var lastLocation: Location? = null
    private var lastWeatherLocation: WeatherLocation? = null
    private var lastRequestTimestamp = -REQUEST_THRESHOLD

    private lateinit var job: Job

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate() {
        super.onCreate()
        job = Job()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onConnected() {
        super.onConnected()
        repo.apiKey = sp.getString(API_KEY, null)
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDisconnected() {
        super.onDisconnected()
        sp.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onRequestSubmitted(request: ServiceRequest) {
        val requestInfo = request.requestInfo
        val requestType = requestInfo.requestType
        if (((requestType == RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ &&
                    isSameGeoLocation(requestInfo.location, lastLocation)) ||
                    (requestType == RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ &&
                            isSameWeatherLocation(requestInfo.weatherLocation, lastWeatherLocation))) &&
            wasRequestSubmittedTooSoon()) {
            request.reject(LineageWeatherManager.RequestStatus.SUBMITTED_TOO_SOON)
            return
        }
        when (requestType) {
            RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ,
            RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ -> {
                updateWeather(request)
            }
            RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ -> {
                findLocationsByCityName(request)
            }
        }
    }

    override fun onRequestCancelled(request: ServiceRequest) {
        job.cancel()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == API_KEY) {
            repo.apiKey = sp.getString(API_KEY, null)
        }
    }

    private fun updateWeather(request: ServiceRequest) {
        launch {
            val weatherInfo = withContext(Dispatchers.IO) {
                val requestInfo = request.requestInfo
                when (requestInfo.requestType) {
                    RequestInfo.TYPE_WEATHER_BY_WEATHER_LOCATION_REQ -> {
                        try {
                            repo.queryWeather(requestInfo.weatherLocation)
                        } catch (_: InvalidApiKeyException) {
                            setApiKeyVerified(API_KEY_INVALID)
                            null
                        }
                    }
                    RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ -> {
                        try {
                            repo.queryWeather(requestInfo.location)
                        } catch (_: InvalidApiKeyException) {
                            setApiKeyVerified(API_KEY_INVALID)
                            null
                        }
                    }
                    else -> null
                }
            }
            if (weatherInfo == null) {
                request.fail()
            } else {
                val result = ServiceRequestResult.Builder(weatherInfo).build()
                request.complete(result)
                when (request.requestInfo.requestType) {
                    RequestInfo.TYPE_WEATHER_BY_GEO_LOCATION_REQ -> {
                        lastLocation = request.requestInfo.location
                    }
                    else -> {
                        lastWeatherLocation = request.requestInfo.weatherLocation
                    }
                }
                setApiKeyVerified(API_KEY_VERIFIED)
            }
        }
    }

    private fun findLocationsByCityName(request: ServiceRequest) {
        launch {
            val locations = withContext(Dispatchers.IO) {
                val requestInfo = request.requestInfo
                if (requestInfo.requestType != RequestInfo.TYPE_LOOKUP_CITY_NAME_REQ) {
                    null
                } else {
                    try {
                        repo.lookupCity(requestInfo.cityName)
                    } catch (_: InvalidApiKeyException) {
                        setApiKeyVerified(API_KEY_INVALID)
                        null
                    }
                }
            }
            if (locations == null) {
                request.fail()
            } else {
                request.complete(ServiceRequestResult.Builder(locations).build())
                setApiKeyVerified(API_KEY_VERIFIED)
            }
        }
    }

    private fun setApiKeyVerified(state: Int) {
        sp.edit().putInt(API_KEY_VERIFIED_STATE, state).apply()
    }

    private fun isSameWeatherLocation(
        newLocation: WeatherLocation?,
        oldLocation: WeatherLocation?
    ): Boolean {
        return if (newLocation == null || oldLocation == null) false else newLocation.cityId == oldLocation.cityId
                && newLocation.city == oldLocation.city
                && newLocation.postalCode == oldLocation.postalCode
                && newLocation.country == oldLocation.country
                && newLocation.countryId == oldLocation.countryId
    }

    private fun isSameGeoLocation(newLocation: Location?, oldLocation: Location?): Boolean {
        if (newLocation == null || oldLocation == null) return false
        val distance = newLocation.distanceTo(oldLocation)
        return distance < LOCATION_DISTANCE_METERS_THRESHOLD
    }

    private fun wasRequestSubmittedTooSoon(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return lastRequestTimestamp + REQUEST_THRESHOLD > now
    }
}