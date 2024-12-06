package edu.tcu.sameepshah.weather

import android.Manifest
import android.content.pm.PackageManager
import android.icu.util.TimeZone
import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import edu.tcu.sameepshah.weather.databinding.ActivityMainBinding
import edu.tcu.sameepshah.weather.model.Place
import edu.tcu.sameepshah.weather.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var view: View
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var weatherService: WeatherService
    private lateinit var weatherResponse: WeatherResponse
    private lateinit var geoService: GeoService
    private lateinit var geoResponse: List<Place>

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Snackbar.make(view, getString(R.string.location_permission_granted), Snackbar.LENGTH_SHORT).show()
//                updateLocationAndWeatherRepeatedly()
            } else {
                Snackbar.make(view, getString(R.string.location_permission_denied), Snackbar.LENGTH_SHORT).show()
            }
        }

    private var cancellationTokenSource: CancellationTokenSource? = null
    private var weatherServiceCall: Call<WeatherResponse>? = null
    private var geoServiceCall: Call<List<Place>>? = null
    private var updateJob: Job? = null
    private var delayJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        view = binding.root // or view = binding.main
        setContentView(view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val retrofit = Retrofit.Builder()
            .baseUrl(getString(R.string.base_url))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        weatherService = retrofit.create(WeatherService::class.java)
        geoService = retrofit.create(GeoService::class.java)
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                updateLocationAndWeatherRepeatedly()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                // if Ok is clicked, show the prompt/launcher
                Snackbar.make(view, getString(R.string.location_permission_required), Snackbar.LENGTH_INDEFINITE)
                    .setAction("OK") {
                        requestPermissionLauncher.launch(
                            Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun cancelRequests() {
        cancellationTokenSource?.cancel()
        weatherServiceCall?.cancel()
        geoServiceCall?.cancel()
        updateJob?.cancel()
    }

    private fun updateLocationAndWeatherRepeatedly() {
        // We need to put this in IO call routine as we want to update the data every 15 seconds
        // but that is over the 5 sec limit that causes the app to crash under Android protocol
        delayJob = lifecycleScope.launch(Dispatchers.IO) {
            while(true) {
                // IO cannot deal with UI, so we need to put something in the main call routine
                updateJob = launch(Dispatchers.Main) { updateLocationAndWeather() }
                delay(15000)
                cancelRequests()
            }
        }
    }

    private fun updateLocationAndWeather() {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> {
                cancellationTokenSource = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation (
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource?.token).addOnSuccessListener {
                        if (it != null) {
                            updateWeather(it)
                        } else {
                            displayUpdateFailed()
                        }
                }
            }
        }
    }

    override fun onDestroy() {
        cancelRequests()
        delayJob?.cancel()
        super.onDestroy()
    }

    private fun updateWeather(location: Location) {
        weatherServiceCall = weatherService.getWeather(
            location.latitude,
            location.longitude,
            getString(R.string.appid),
            "imperial"
        )
        weatherServiceCall?.enqueue(
            object: Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    val weatherResponseNullable = response.body()
                    if(weatherResponseNullable != null) {
                        weatherResponse = weatherResponseNullable
                        updatePlace(location)
                        displayWeather()
                    }
                }
                override fun onFailure(p0: Call<WeatherResponse>, p1: Throwable) {
                    displayUpdateFailed()
                }
            }
        )
    }

    private fun updatePlace(location: Location) {
        geoServiceCall = geoService.getGeo(
            location.latitude,
            location.longitude,
            getString(R.string.appid)
        )
        geoServiceCall?.enqueue(
            object: Callback<List<Place>> {
                override fun onResponse(call: Call<List<Place>>, response: Response<List<Place>>) {
                    val geoResponseNullable = response.body()
                    if(geoResponseNullable != null) {
                        geoResponse = geoResponseNullable
                        displayPlace(false)
                    }
                }
                override fun onFailure(p0: Call<List<Place>>, p1: Throwable) {
                    displayUpdateFailed()
                }
            }
        )
    }

    private fun displayUpdateFailed() {
        println("Check your API key.")
    }

    private fun displayPlace(isSuccess: Boolean) {
        binding.placeTv.text = getString(R.string.place, geoResponse[0].name, geoResponse[0].state)
    }

    private fun displayWeather() {
        val description = weatherResponse.weather[0].
            description.split(" ").joinToString(" ") {
                it.replaceFirstChar { char -> char.uppercase() }
        }
        binding.descriptionTv.text =
            getString(R.string.description,
                description,
                weatherResponse.main.temp_max,
                weatherResponse.main.temp_min)

        val utcInMsSunrise = (weatherResponse.sys.sunrise + weatherResponse.timezone) * 1000L - TimeZone.getDefault().rawOffset
        val utcInMsSunset = (weatherResponse.sys.sunset + weatherResponse.timezone) * 1000L - TimeZone.getDefault().rawOffset
        val sunrise = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(utcInMsSunrise))
        val sunset = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(utcInMsSunset))
        binding.sunDataTv.text = getString(R.string.sun_data).format(sunrise, sunset)

        val speed = weatherResponse.wind.speed
        val direction = weatherResponse.wind.deg
        val gust = weatherResponse.wind.gust
        binding.windDataTv.text = getString(R.string.wind_data, speed, direction, gust)

        val humidity = weatherResponse.main.humidity
        val cloudiness = weatherResponse.clouds.all
        binding.precipitationDataTv.text = getString(R.string.precipitation_data, humidity, cloudiness)

        val feelsLike = weatherResponse.main.feels_like
        val visibility = weatherResponse.visibility
        val pressure = weatherResponse.main.pressure
        binding.otherDataTv.text = getString(R.string.other_data,
            feelsLike, metersToMiles(visibility), hpaToinHg(pressure))

        val icon = "ic_" + weatherResponse.weather[0].icon
        with(binding) { conditionIv.setImageResource(resources.getIdentifier(icon, "drawable", packageName)) }

        val temp = weatherResponse.main.temp
        binding.temperatureTv.text = getString(R.string.temperature, temp)
    }

    private fun metersToMiles(meters: Int) : Double {
        return (meters / 1609.34)
    }

    private fun hpaToinHg(hpa : Int) : Double {
        return (hpa / 33.863886666667)
    }
}