package com.example.teamtime

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import com.example.teamtime.uicompose.MyApp
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var permissionRequest: ActivityResultLauncher<String>
    private val radiusInMeters = 50.0 // 50 meters radius
    val officeLatitude = 26.8973744
    val officeLongitude = 75.7559854

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        permissionRequest =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Log.d("Permission", "Location permission granted.")
                } else {
                    Log.d("Permission", "Location permission denied.")
                }
            }

        setContent {
            var showDialog by remember { mutableStateOf(false) }
            var dialogTitle by remember { mutableStateOf("") }
            var dialogMessage by remember { mutableStateOf("") }
            var onDismissAction by remember { mutableStateOf<() -> Unit>({}) }

            MyApp(
                showDialog = showDialog,
                dialogTitle = dialogTitle,
                dialogMessage = dialogMessage,
                onDismissDialog = {
                    showDialog = false
                    onDismissAction()
                },
                onRequestLocation = {
                    if (isLocationEnabled()) {
                        fetchLocationAndInfo(
                            onSuccess = { androidId, latitude, longitude, time ->
                                if (isWithinRadius(latitude, longitude)) {
                                    // Allow Check-in or Check-out
                                    sendDataToGoogleSheets(androidId, latitude, longitude, time)
                                    Toast.makeText(
                                        this,
                                        "Done :)",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // Outside radius, show Toast
                                    Toast.makeText(
                                        this,
                                        "You are not within the required 50-meter radius of the office.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onRedirect = {
                                dialogTitle = "Location Services Disabled"
                                dialogMessage = "Please enable location services to proceed..."
                                onDismissAction = { redirectToLocationSettings() }
                                showDialog = true
                            }
                        )
                    } else {
                        dialogTitle = "Location Services Disabled"
                        dialogMessage = "Please enable location services to proceed..."
                        onDismissAction = { redirectToLocationSettings() }
                        showDialog = true
                    }
                }
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndInfo(
        onSuccess: (androidId: String, latitude: Double, longitude: Double, time: String) -> Unit,
        onRedirect: () -> Unit
    ) {
        if (hasLocationPermission()) {
            val locationRequest = LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            val locationCallback = object : LocationCallback() {
                @SuppressLint("HardwareIds")
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.locations[0]
                    if (location != null) {
                        val androidId = Settings.Secure.getString(
                            contentResolver,
                            Settings.Secure.ANDROID_ID
                        )
                        val latitude = location.latitude
                        val longitude = location.longitude
                        val currentTime = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                        ).format(Date())

                        fusedLocationClient.removeLocationUpdates(this)
                        onSuccess(androidId, latitude, longitude, currentTime)
                    } else {
                        onRedirect()
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        permissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun redirectToLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("LocationRedirect", "Error redirecting to location settings: ${e.message}")
        }
    }

    private fun isWithinRadius(latitude: Double, longitude: Double): Boolean {
        val distance = calculateDistance(officeLatitude, officeLongitude, latitude, longitude)
        return distance <= radiusInMeters
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Radius of Earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c * 1000 // Distance in meters
    }

    private fun sendDataToGoogleSheets(
        androidId: String,
        latitude: Double,
        longitude: Double,
        time: String
    ) {
        Thread {
            try {
                val url = URL("https://script.google.com/macros/s/AKfycbwtRixMSTS07ZDTh7vkQ9NABFo6LUMDZbMMVdeQg9EpfSDbJQLyZOpIBOJ-oT8dNuzgcA/exec")
                val postData =
                    "androidId=$androidId&latitude=$latitude&longitude=$longitude&time=$time"

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                // Write data to the request
                connection.outputStream.use { it.write(postData.toByteArray()) }

                val responseCode = connection.responseCode
                val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("GoogleSheets", "Data sent successfully: $responseMessage")
                } else {
                    Log.e("GoogleSheets", "Failed to send data. Response Code: $responseCode")
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e("GoogleSheets", "Error sending data to Google Sheets: ${e.message}")
            }
        }.start()
    }
}