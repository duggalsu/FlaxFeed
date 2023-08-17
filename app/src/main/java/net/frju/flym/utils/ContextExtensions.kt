/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package net.frju.flym.utils

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import org.jetbrains.anko.defaultSharedPreferences

fun Context.isOnline(context: Context): Boolean {
 //  = connectivityManager.activeNetworkInfo?.isConnected == true
    // https://stackoverflow.com/questions/68444018/network-state-with-jetpack-compose
    var result = false
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        result = when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    } else {
        connectivityManager.run {
            connectivityManager.activeNetworkInfo?.run {
                result = when (type) {
                    ConnectivityManager.TYPE_WIFI -> true
                    ConnectivityManager.TYPE_MOBILE -> true
                    ConnectivityManager.TYPE_ETHERNET -> true
                    else -> false
                }
            }
        }
    }
    return result
}

fun Context.getPrefBoolean(key: String, defValue: Boolean) =
        defaultSharedPreferences.getBoolean(key, defValue)

fun Context.putPrefBoolean(key: String, value: Boolean) =
        defaultSharedPreferences.edit { putBoolean(key, value) }


fun Context.getPrefInt(key: String, defValue: Int) =
        defaultSharedPreferences.getInt(key, defValue)

fun Context.putPrefInt(key: String, value: Int) =
        defaultSharedPreferences.edit { putInt(key, value) }

fun Context.getPrefLong(key: String, defValue: Long) =
        defaultSharedPreferences.getLong(key, defValue)

fun Context.putPrefLong(key: String, value: Long) =
        defaultSharedPreferences.edit { putLong(key, value) }

fun Context.getPrefString(key: String, defValue: String) =
        defaultSharedPreferences.getString(key, defValue)

fun Context.putPrefString(key: String, value: String) =
        defaultSharedPreferences.edit { putString(key, value) }

fun Context.getPrefStringSet(key: String, defValue: MutableSet<String>) =
        defaultSharedPreferences.getStringSet(key, defValue)

fun Context.putPrefStringSet(key: String, value: MutableSet<String>) =
        defaultSharedPreferences.edit { putStringSet(key, value) }

fun Context.removePref(key: String) =
        defaultSharedPreferences.edit { remove(key) }

fun Context.registerOnPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    try {
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    } catch (ignored: Exception) { // Seems to be possible to have a NPE here... Why??
    }
}

fun Context.unregisterOnPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    try {
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    } catch (ignored: Exception) { // Seems to be possible to have a NPE here... Why??
    }
}

fun Context.showAlertDialog(
        title: Int,
        function: () -> (Unit)
) {
    AlertDialog.Builder(this)
            .setTitle(title)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                function()
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
}

fun Context.isGestureNavigationEnabled(): Boolean {
    return Settings.Secure.getInt(this.contentResolver, "navigation_mode", 0) == 2
}
