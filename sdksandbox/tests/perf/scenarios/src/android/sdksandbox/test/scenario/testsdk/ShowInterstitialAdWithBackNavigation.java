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

package android.sdksandbox.test.scenario.testsdk;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@Scenario
@RunWith(JUnit4.class)
public class ShowInterstitialAdWithBackNavigation {

    private static final String CUJ_LATENCY_PREFIX = "cuj_latency";
    private static final UiDevice sUiDevice = UiDevice.getInstance(getInstrumentation());
    private static final Bundle sArgsBundle = InstrumentationRegistry.getArguments();
    private static final int WAIT_TIME_BEFORE_END_TEST_MS = 3000;

    protected static String sPackageName;
    private static ClientAppUtils sClientAppUtils;

    @BeforeClass
    public static void setupArguments() throws RemoteException {
        sClientAppUtils = new ClientAppUtils(sUiDevice, sArgsBundle);
        sPackageName = sClientAppUtils.getClientPackageName();
    }

    @AfterClass
    public static void tearDown() throws IOException, RemoteException {
        if (sClientAppUtils != null) {
            sUiDevice.executeShellCommand(sClientAppUtils.getStopAppCommand());
        }
    }

    @Before
    public void setup() throws Exception {
        sUiDevice.executeShellCommand(sClientAppUtils.getStartAppCommand());
        sClientAppUtils.initializeSdk();
    }

    @Test
    public void testShowInterstitialAdWithBackNavigation() throws Exception {
        sClientAppUtils.loadInterstitialAd();
        sClientAppUtils.showInterstitialAd();

        long start = SystemClock.uptimeMillis();
        assertThat(sUiDevice.pressBack()).isTrue();
        long end = SystemClock.uptimeMillis();

        // This log will be used by TimingCollector.
        Log.i(
                "ForTimingCollector",
                CUJ_LATENCY_PREFIX + "_INTERSTITIAL_AD_BACK_NAVIGATION:" + (end - start));

        // Allow metrics to stabilize after CUJ completion.
        Thread.sleep(WAIT_TIME_BEFORE_END_TEST_MS);
    }
}
