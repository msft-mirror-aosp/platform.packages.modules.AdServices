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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class NetworkConnectionHelperTest extends SharedMockitoTestCase {
    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private NetworkCapabilities mMockNetworkCapabilities;

    @Before
    public void setup() {
        mockNetworkCapabilities();
    }

    @Test
    public void testIsWifiConnected_whenConnected() {
        mockActiveNetwork(mock(android.net.Network.class));
        mockNetworkCapabilities(
                mMockNetworkCapabilities,
                /* transport= */ true,
                /* capability= */ true,
                /* internet= */ false);

        assertThat(NetworkConnectionHelper.isWifiConnected(mMockContext)).isTrue();
    }

    @Test
    public void testIsWifiConnected_whenDisconnected() {
        mockActiveNetwork(null);

        expect.withMessage("isWifiConnected() when getActiveNetwork() is null")
                .that(NetworkConnectionHelper.isWifiConnected(mMockContext))
                .isFalse();

        mockActiveNetwork(mock(android.net.Network.class));
        mockNetworkCapabilities(
                mMockNetworkCapabilities,
                /* transport= */ false,
                /* capability= */ false,
                /* internet= */ false);

        expect.withMessage(
                        "isWifiConnected() when hasTransport() returns false and hasCapability()"
                                + " returns false")
                .that(NetworkConnectionHelper.isWifiConnected(mMockContext))
                .isFalse();

        mockNetworkCapabilities(
                mMockNetworkCapabilities,
                /* transport= */ true,
                /* capability= */ false,
                /* internet= */ false);

        expect.withMessage(
                        "isWifiConnected() when hasTransport() returns true and hasCapability()"
                                + " returns false")
                .that(NetworkConnectionHelper.isWifiConnected(mMockContext))
                .isFalse();
    }

    @Test
    public void testIsInternetConnected_whenConnected() {
        mockActiveNetwork(mock(android.net.Network.class));
        mockNetworkCapabilities(
                mMockNetworkCapabilities,
                /* transport= */ true,
                /* capability= */ true,
                /* internet= */ true);

        assertThat(NetworkConnectionHelper.isInternetConnected(mMockContext)).isTrue();
    }

    @Test
    public void testIsInternetConnected_whenDisconnected() {
        mockActiveNetwork(null);

        expect.withMessage("isWifiConnected() when getActiveNetwork() is null")
                .that(NetworkConnectionHelper.isInternetConnected(mMockContext))
                .isFalse();

        mockActiveNetwork(mock(android.net.Network.class));
        mockNetworkCapabilities(
                mMockNetworkCapabilities,
                /* transport= */ false,
                /* capability= */ false,
                /* internet= */ false);

        expect.withMessage(
                        "isInternetConnected() when hasInternet() returns false and hasCapability()"
                                + " returns false")
                .that(NetworkConnectionHelper.isInternetConnected(mMockContext))
                .isFalse();

        mockNetworkCapabilities(
                mMockNetworkCapabilities,
                /* transport= */ true,
                /* capability= */ false,
                /* internet= */ true);

        expect.withMessage(
                        "isWifiConnected() when hasInternet() returns true and hasCapability()"
                                + " returns false")
                .that(NetworkConnectionHelper.isInternetConnected(mMockContext))
                .isFalse();
    }

    private void mockNetworkCapabilities() {
        when(mMockContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
    }

    private void mockActiveNetwork(Network network) {
        when(mMockConnectivityManager.getActiveNetwork()).thenReturn(network);
    }

    private void mockNetworkCapabilities(
            NetworkCapabilities networkCapabilities,
            boolean transport,
            boolean capability,
            boolean internet) {
        when(mMockConnectivityManager.getNetworkCapabilities(any()))
                .thenReturn(networkCapabilities);
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                .thenReturn(transport);
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                .thenReturn(capability);
        when(mMockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .thenReturn(internet);
    }
}
