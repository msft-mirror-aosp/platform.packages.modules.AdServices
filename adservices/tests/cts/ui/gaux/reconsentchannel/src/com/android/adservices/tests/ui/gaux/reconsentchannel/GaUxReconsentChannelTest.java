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
package com.android.adservices.tests.ui.gaux.reconsentchannel;

import static com.android.adservices.tests.ui.libs.UiConstants.LAUNCH_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.UiConstants.UX;
import com.android.adservices.tests.ui.libs.UiUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@RunWith(AndroidJUnit4.class)
public class GaUxReconsentChannelTest {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        UiUtils.enableNotificationPermission();

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiUtils.refreshConsentResetToken();
        UiUtils.restartAdservices();
        UiUtils.turnOnEnableAdsServicesAPI();
        UiUtils.disableConsentDebugMode();
        UiUtils.disableSchedulingParams();
        mCommonManager = AdServicesCommonManager.get(sContext);

        // General purpose callback used for expected success calls.
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

        // Reset consent and thereby AdServices data before each test.

        SettableFuture<Boolean> responseFuture = SettableFuture.create();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<Boolean, Exception>() {
                    @Override
                    public void onResult(Boolean result) {
                        responseFuture.set(result);
                    }

                    @Override
                    public void onError(Exception exception) {
                        responseFuture.setException(exception);
                    }
                });

        Boolean response = responseFuture.get();
        assertThat(response).isTrue();

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        if (!AdservicesTestHelper.isDeviceSupported()) return;

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    /**
     * Verify that for Beta ROW devices with non-zeroed-out AdId, the ROW notification is displayed.
     * and after we opt-out consent, and move to GA, the reconsent does not trigger.
     */
    @Test
    public void testBetaRowOptoutNoReconsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, /* isGa */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, false, false);

        UiUtils.enableGa();
        // Notifications should not be shown if beta consent false.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
    }

    /**
     * Verify that for Beta ROW devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-out consent, and move to GA, the reconsent does not trigger.
     */
    @Test
    public void testBetaRowAdIdDisabledOptoutNoReconsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, true, false);

        UiUtils.enableGa();
        // Notifications should not be shown if beta consent false.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ true, UX.GA_UX);
    }

    /**
     * Verify that for Beta Eu devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-out consent, and move to GA, the reconsent does not trigger.
     */
    @Test
    public void testBetaEuAdIdDisabledOptoutNoReconsent() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, true, false);

        UiUtils.enableGa();
        // Notifications should not be shown if beta consent false.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ true, UX.GA_UX);
    }

    /**
     * Verify that for Beta ROW devices with non-zeroed-out AdId, the ROW notification is displayed.
     * And after we opt-in consent, and move to GA, the reconsent trigger.
     */
    @Test
    public void testBetaRowOptinReconsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, /* isGa */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, false, true);

        // Wait for consent operation finish
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        mDevice.pressHome();
        UiUtils.enableGa();
        // Notifications should show if beta consent true.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.GA_UX);
    }

    /**
     * Verify that for Beta ROW devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-in consent, and move to GA, the EU reconsent trigger.
     */
    @Test
    public void testBetaRowAdIdDisabledOptinReconsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, true, true);

        // Wait for consent operation finish
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiUtils.enableGa();
        // Notifications should not be shown if beta consent false.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, UX.GA_UX);
    }

    /**
     * Verify that for Beta EU devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-in consent, and move to GA, the EU reconsent trigger.
     */
    @Test
    public void testBetaEuAdIdDisabledOptinReconsent() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, /* isGa */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, true, true);

        // Wait for consent operation finish
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiUtils.enableGa();
        // Notifications should not be shown if beta consent false.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        UiUtils.verifyNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, UX.GA_UX);
    }
}
