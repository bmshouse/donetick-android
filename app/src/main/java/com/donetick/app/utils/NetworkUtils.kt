package com.donetick.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Utility class for network-related operations
 */
object NetworkUtils {

    /**
     * Checks if the device has an active network connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /**
     * Gets a user-friendly network error message
     */
    fun getNetworkErrorMessage(context: Context): String {
        return if (isNetworkAvailable(context)) {
            "Unable to connect to server. Please check the server URL and try again."
        } else {
            "No internet connection. Please check your network settings and try again."
        }
    }
}
