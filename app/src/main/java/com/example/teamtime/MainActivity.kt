package com.example.teamtime

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
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
import com.google.android.gms.location.LocationServices
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var permissionRequest: ActivityResultLauncher<String>

    // Office static location (example)
    private val officeLatitude = 26.8973681  // Office Latitude
    private val officeLongitude = 75.7559878 // Office Longitude

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
                        fetchLocationAndCheckInOut(
                            onSuccess = { androidId, latitude, longitude, time ->
                                Toast.makeText(
                                    this,
                                    "Done :)",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Send data to Google Sheets after success
                                sendDataToGoogleSheets(androidId, latitude, longitude, time)
                            },
                            onError = {
                                dialogTitle = "Error Fetching Location"
                                dialogMessage = "Unable to fetch location. Please try again."
                                onDismissAction = {}
                                showDialog = true
                            },
                            onRedirect = {
                                dialogTitle = "Location Services Disabled"
                                dialogMessage = "Please enable location services to proceed."
                                onDismissAction = { redirectToLocationSettings() }
                                showDialog = true
                            }
                        )
                    } else {
                        dialogTitle = "Location Services Disabled"
                        dialogMessage = "Please enable location services to proceed."
                        onDismissAction = { redirectToLocationSettings() }
                        showDialog = true
                    }
                }
            )
        }
    }

    // Method to calculate the distance between the current location and the office location
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Radius of the Earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c * 1000 // Distance in meters
    }

    // Method to check if the user is within 100 meters of the office location
    private fun checkInOrCheckOut(latitude: Double, longitude: Double): Boolean {
        // Calculate distance to office
        val distance = calculateDistance(latitude, longitude, officeLatitude, officeLongitude)

        return distance <= 5 // Return true if within 100 meters
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun fetchLocationAndCheckInOut(
        onSuccess: (androidId: String, latitude: Double, longitude: Double, time: String) -> Unit,
        onError: () -> Unit,
        onRedirect: () -> Unit
    ) {
        if (hasLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val androidId = android.provider.Settings.Secure.getString(
                        contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val currentTime =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    Log.d("LocationInfo", "Android ID: $androidId")
                    Log.d("LocationInfo", "Latitude: $latitude")
                    Log.d("LocationInfo", "Longitude: $longitude")
                    Log.d("LocationInfo", "Time: $currentTime")

                    // Check if user is within 100 meters of the office
                    if (checkInOrCheckOut(latitude, longitude)) {
                        // Proceed with sending data if within range
                        onSuccess(androidId, latitude, longitude, currentTime)
                    } else {
                        // Notify user they are outside the 100-meter range
                        Toast.makeText(this, "You are not within 100 meters of the office.", Toast.LENGTH_SHORT).show()
                        onError()
                    }
                } else {
                    Log.e("LocationError", "Location is null, redirecting to location settings.")
                    onRedirect()
                }
            }.addOnFailureListener { exception ->
                Log.e("LocationError", "Location fetch failed: ${exception.message}")
                onRedirect()
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
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
            Log.e("LocationRedirect", "Error while redirecting to location settings: ${e.message}")
        }
    }

    private fun sendDataToGoogleSheets(androidId: String, latitude: Double, longitude: Double, time: String) {
        Thread {
            try {
                val url = URL("https://script.google.com/macros/s/AKfycbwtRixMSTS07ZDTh7vkQ9NABFo6LUMDZbMMVdeQg9EpfSDbJQLyZOpIBOJ-oT8dNuzgcA/exec")
                val postData = "androidId=$androidId&latitude=$latitude&longitude=$longitude&time=$time"

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                // Write data to the output stream
                val outputStream: OutputStream = connection.outputStream
                outputStream.write(postData.toByteArray())
                outputStream.flush()

                val responseCode = connection.responseCode
                /*if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("GoogleSheets", "Data sent successfully.")
                    runOnUiThread {
                        Toast.makeText(this, "Data sent successfully to Google Sheets.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("GoogleSheets", "Failed to send data. Response Code: $responseCode")
                }*/

                connection.disconnect()
            } catch (e: Exception) {
                Log.e("GoogleSheets", "Error sending data to Google Sheets: ${e.message}")
            }
        }.start()
    }
}
