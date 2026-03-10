package ca.deltica.contactra.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Helper for retrieving device location and performing reverse geocoding. Use
 * suspend functions with coroutines for asynchronous operations.
 */
object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? {
        val fusedLocationProvider: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
        return try {
            fusedLocationProvider.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    fun reverseGeocode(context: Context, location: Location): Pair<String?, String?> {
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val city = firstMeaningful(
                    address.locality,
                    address.subAdminArea,
                    address.adminArea
                )
                val street = listOf(address.subThoroughfare, address.thoroughfare)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")
                    .takeIf { isMeaningfulPlace(it) }
                val placeName = firstMeaningful(
                    address.premises,
                    street,
                    address.thoroughfare,
                    address.subLocality,
                    address.featureName,
                    address.getAddressLine(0)
                ) ?: city
                placeName to city
            } else {
                null to null
            }
        } catch (e: Exception) {
            null to null
        }
    }

    private fun firstMeaningful(vararg values: String?): String? {
        return values.firstOrNull { isMeaningfulPlace(it) }?.trim()
    }

    private fun isMeaningfulPlace(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        return trimmed.any { it.isLetter() }
    }
}
