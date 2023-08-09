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
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
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
        AdservicesTestHelper.killAdservicesProcess(sContext);
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
        AdservicesTestHelper.killAdservicesProcess(sContext);
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
        reconsentTestHelper(false, false, false, true, false);
    }

    /**
     * Verify that for Beta ROW devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-out consent, and move to GA, the reconsent does not trigger.
     */
    @Test
    public void testBetaRowAdIdDisabledOptoutNoReconsent() throws Exception {
        reconsentTestHelper(false, false, true, false, false);
    }

    /**
     * Verify that for Beta Eu devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-out consent, and move to GA, the reconsent does not trigger.
     */
    @Test
    public void testBetaEuAdIdDisabledOptoutNoReconsent() throws Exception {
        reconsentTestHelper(false, true, true, false, false);
    }

    /**
     * Verify that for Beta ROW devices with non-zeroed-out AdId, the ROW notification is displayed.
     * And after we opt-in consent, and move to GA, the reconsent trigger.
     */
    @Test
    public void testBetaRowOptinReconsent() throws Exception {
        reconsentTestHelper(true, false, false, true, true);
    }

    /**
     * Verify that for Beta ROW devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-in consent, and move to GA, the EU reconsent trigger.
     */
    @Test
    public void testBetaRowAdIdDisabledOptinReconsent() throws Exception {
        reconsentTestHelper(true, false, true, false, true);
    }

    /**
     * Verify that for Beta EU devices with zeroed-out AdId, the EU notification is displayed. And
     * after we opt-in consent, and move to GA, the EU reconsent trigger.
     */
    @Test
    public void testBetaEuAdIdDisabledOptinReconsent() throws Exception {
        reconsentTestHelper(true, true, true, false, true);
    }

    private void reconsentTestHelper(
            boolean isDisplayed,
            boolean isEuDevice,
            boolean isEuNotification,
            boolean isAdidEnabled,
            boolean isOptin)
            throws Exception {
        if (isEuDevice) {
            UiUtils.setAsEuDevice();
        } else {
            UiUtils.setAsRowDevice();
        }
        UiUtils.enableBeta();

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(isAdidEnabled)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);
        AdservicesWorkflows.testClickNotificationFlow(
                sContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ isEuNotification,
                /* ux type */ UX.BETA_UX,
                /* isFlipFlow */ false,
                /* consent opt-in */ isOptin);

        // Wait for consent operation finish
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiUtils.enableGa();
        AdservicesTestHelper.killAdservicesProcess(sContext);
        // Notifications should not be shown if beta consent false.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice,
                /* isDisplayed */ isDisplayed,
                /* isEuTest */ isEuNotification,
                /* ux type */ UX.GA_UX);
    }
}
