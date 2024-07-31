/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.shared.testing.network;

import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

/** Helper for device network related functions */
public final class NetworkConnectionHelper {

    /** Checks whether the device has an active Wifi connected. */
    // The test using this helper class needs to add the ACCESS_NETWORK_STATE permission.
    @SuppressLint("MissingPermission")
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager =
                context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

        return networkCapabilities != null
                && networkCapabilities.hasTransport(TRANSPORT_WIFI)
                && networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED);
    }

    private NetworkConnectionHelper() {
        throw new UnsupportedOperationException("provides only static methods");
    }
}
