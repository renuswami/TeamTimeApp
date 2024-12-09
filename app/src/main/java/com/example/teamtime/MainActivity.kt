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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private val officeLatitude = 26.8973744
    private val officeLongitude = 75.7559854

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

                                    //checkAndUpdateData("30a01419478b8bf4", 26.897367, 75.7559097, "2024-12-07 15:10:12")
                                    //sendDataToGoogleSheets("30a01419478b8bf4", 26.897367, 75.7559097, "2024-12-07 12:14:12")
                                    checkAndUpdateData(androidId, latitude, longitude, time)
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
                val url =
                    URL("https://script.google.com/macros/s/AKfycbwtRixMSTS07ZDTh7vkQ9NABFo6LUMDZbMMVdeQg9EpfSDbJQLyZOpIBOJ-oT8dNuzgcA/exec")
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


private fun sendDataToFirebase(
    androidId: String,
    latitude: Double,
    longitude: Double,
    time: String
) {
    // Initialize the Firestore reference
    val firestore = FirebaseFirestore.getInstance()

    // Data to be stored
    val data = hashMapOf(
        "androidId" to androidId,
        "latitude" to latitude,
        "longitude" to longitude,
        "time" to time
    )

    // Add data to the "employees" collection
    firestore.collection("employees")
        .add(data)
        .addOnSuccessListener { documentReference ->
            println("Data successfully written with ID: ${documentReference.id}")
        }
        .addOnFailureListener { error ->
            println("Failed to write data to Firebase Firestore: ${error.message}")
        }
}

fun checkAndUpdateData(androidId: String, latitude: Double, longitude: Double, time: String) {
    val db = FirebaseFirestore.getInstance()

    // Reference to the "employees" collection
    val employeesRef = db.collection("employees")

    // Parse the time to get the date
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val currentDate = try {
        dateFormat.parse(time)
    } catch (e: ParseException) {
        e.printStackTrace()
        null
    }

    if (currentDate != null) {
        // Fetch the existing records with the same androidId and date
        employeesRef
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { data ->
                val matchingEntries = mutableListOf<DocumentSnapshot>()

                // Loop through the data to find records with the same date
                for (document in data) {
                    val existingTime = document.getString("time")
                    val existingDate = try {
                        dateFormat.parse(existingTime)
                    } catch (e: ParseException) {
                        null
                    }

                    // If the dates match, add the entry to the matchingEntries list
                    if (existingDate != null && isSameDay(currentDate, existingDate)) {
                        matchingEntries.add(document)
                    }
                }

                // Check how many matching entries were found
                when (matchingEntries.size) {
                    0 -> {
                        // If no matching entries found, insert the new data
                        sendDataToFirebase(androidId, latitude, longitude, time)
                    }
                    1 -> {
                        // If exactly one matching entry is found, update it (this would be the first entry)
                        sendDataToFirebase(androidId, latitude, longitude, time)
                    }
                    2 -> {
                        // If two matching entries are found, update the second one
                        val secondEntry = matchingEntries[1]
                        val documentId = secondEntry.id
                        val updatedData = hashMapOf(
                            "androidId" to androidId,
                            "latitude" to latitude,
                            "longitude" to longitude,
                            "time" to time
                        )

                        // Update the second entry in Firestore
                        employeesRef.document(documentId)
                            .set(updatedData)
                            .addOnSuccessListener {
                                println("Second entry updated successfully.")
                            }
                            .addOnFailureListener { error ->
                                println("Failed to update second entry: ${error.message}")
                            }
                    }
                    else -> {
                        println("Error: More than two matching entries found.")
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.d("FirestoreData", e.toString())
                println("Error fetching documents: $e")
            }
    }
}

// Utility function to check if two dates are the same day
fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance()
    cal1.time = date1
    val cal2 = Calendar.getInstance()
    cal2.time = date2

    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
            cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
}
