/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.tests.ui.gaux;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@RunWith(AndroidJUnit4.class)
public class GaUxNotificationTriggerTest {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // TO-DO (b/271567864): grant the permission in our apk code and remove this in the future.
        // Grant runtime permission to the AOSP adservices app.
        UiUtils.enableNotificationPermission();

        mCommonManager = AdServicesCommonManager.get(sContext);

        // consent debug mode is turned on for this test class as we only care about the
        // first trigger (API call).
        UiUtils.enableConsentDebugMode();

        mCallback =
                new OutcomeReceiver<Boolean, Exception>() {
                    @Override
                    public void onResult(Boolean result) {
                        assertThat(result).isTrue();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Assert.fail();
                    }
                };

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        if (!AdservicesTestHelper.isDeviceSupported()) return;

        mDevice.pressHome();
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    /** Verify that the API returns false when API is disabled. */
    @Test
    public void testApiDisabled() throws Exception {
        UiUtils.turnOffEnableAdsServicesAPI();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        assertThat(result).isFalse();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Assert.fail();
                    }
                });

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, /* isGa */ true);

        UiUtils.turnOnEnableAdsServicesAPI();
    }

    /** Verify that entry point disabled can not trigger consent notification. */
    @Test
    public void testEntryPointDisabled() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(false)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, /* isGa */ true);
    }

    /** Verify that non-adult account can not trigger consent notification. */
    @Test
    public void testNonAdultAccount() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(false)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, /* isGa */ true);
    }

    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed.
     */
    @Test
    public void testGaRowAdIdEnabled() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, /* isGa */ true);
    }

    /**
     * Verify that for GA, ROW devices with zeroed-out AdId, the GA EU notification is displayed.
     */
    @Test
    public void testGaRowAdIdDisabled() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableGa();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ true);
    }

    /**
     * Verify that for GA, EU devices with non zeroed-out AdId, the GA EU notification is displayed.
     */
    @Test
    public void testGaEuAdIdEnabled() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.enableGa();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ true);
    }

    /** Verify that for GA, EU devices with zeroed-out AdId, the EU notification is displayed. */
    @Test
    public void testGaEuAdIdDisabled() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.enableGa();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ true);
    }
}
