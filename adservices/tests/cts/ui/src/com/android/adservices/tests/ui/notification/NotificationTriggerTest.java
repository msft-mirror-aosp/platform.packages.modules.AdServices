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
package com.android.adservices.tests.ui.notification;

import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_DISABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_DISABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_ENABLED;

import android.adservices.common.AdServicesCommonManager;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.tests.ui.libs.UiUtils;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@RunWith(AndroidJUnit4.class)
public class NotificationTriggerTest {

    private AdServicesCommonManager mCommonManager;
    private UiDevice mDevice;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private Map<String, String> mInitialParams;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mCommonManager = AdServicesCommonManager.get(sContext);

        // consent debug mode is turned off for this test class as we only care about the
        // first trigger (API call).
        UiUtils.disableConsentDebugMode();
        UiUtils.disableSchedulingParams();
        mInitialParams = UiUtils.getInitialParams(/* getSimRegion */ true);
        UiUtils.setSourceOfTruthToPPAPI();
        UiUtils.clearSavedStatus();
        UiUtils.restartAdservices();
    }

    @After
    public void tearDown() throws Exception {
        UiUtils.resetInitialParams(mInitialParams);
    }

    /** Verify no notification is displayed when the entry point is disabled for EU devices. */
    @Test
    public void testEuEntryPointDisabled() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.disableGaUxFeature();
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_DISABLED, AD_ID_ENABLED);

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ true);
    }

    /** Verify no notification is displayed when the entry point is disabled for ROW devices. */
    @Test
    public void testRowEntryPointDisabled() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_DISABLED, AD_ID_ENABLED);

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ true);
    }

    /** Verify that for EU devices with zeroed-out AdId, the EU notification is displayed. */
    @Test
    public void testEuAdIdDisabled() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.disableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /** Verify that for ROW devices with zeroed-out AdId, the EU notification is displayed. */
    @Test
    public void testRowAdIdDisabled() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /** Verify that for EU devices with non zeroed-out AdId, the EU notification is displayed. */
    @Test
    public void testEuAdIdEnabled() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.disableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /** Verify that for ROW devices with non zeroed-out AdId, the ROW notification is displayed. */
    @Test
    public void testRowAdIdEnabled() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);
    }

    /**
     * Verify that for EU devices with non zeroed-out AdId, and GA UX feature enabled, the EU GA UX
     * notification is displayed.
     */
    @Test
    public void testEuAdIdEnabledGaUxEnabledFirstConsent() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /**
     * Verify that for ROW devices with non zeroed-out AdId, and GA UX feature enabled, the ROW GA
     * UX notification is displayed.
     */
    @Test
    public void testRowAdIdEnabledGaUxEnabledFirstConsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);
    }

    /**
     * Verify that for EU devices with zeroed-out AdId, and GA UX feature enabled, the EU
     * notification is displayed.
     */
    @Test
    public void testEuAdIdDisabledGaUxEnabledFirstConsent() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /**
     * Verify that for ROW devices with zeroed-out AdId, and GA UX feature enabled, the EU GA UX
     * notification is displayed as part of the re-consent notification feature.
     */
    @Test
    public void testRowAdIdDisabledGaUxEnabledFirstConsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /**
     * Verify that for ROW devices with zeroed-out AdId, EU notification displayed, and GA UX
     * feature enabled, the EU GA UX notification is displayed as part of the re-consent
     * notification feature.
     */
    @Test
    public void testRowAdIdDisabledGaUxEnabledReConsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);
        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
        UiUtils.consentConfirmationScreen(sContext, mDevice, true, true);

        mDevice.pressHome();
        UiUtils.restartAdservices();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    /**
     * Verify that for ROW devices with non zeroed-out AdId, notification displayed, and GA UX
     * feature enabled, the ROW GA UX notification is displayed as part of the re-consent
     * notification feature.
     */
    @Test
    public void testRowAdIdEnabledGaUxEnabledReConsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, false, true);

        mDevice.pressHome();
        UiUtils.restartAdservices();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);
    }

    /**
     * Verify that for ROW devices with non zeroed-out AdId, notification displayed, and GA UX
     * feature enabled, the GA UX notification is displayed. and second time call, the notification
     * should not displayed
     */
    @Test
    @Ignore("b/269145305")
    public void testRowAdIdEnabledGaUxEnabledReConsentSecondNotDisplayed() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, false, true);

        mDevice.pressHome();
        UiUtils.restartAdservices();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);

        mDevice.pressHome();
        UiUtils.restartAdservices();

        // second time call, notification should not displayed
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false);
    }

    /**
     * Verify that for ROW devices with non zeroed-out AdId, notification displayed, User opt-out
     * consent, and GA UX feature enabled, the GA UX notification is not displayed.
     */
    @Test
    public void testRowAdIdEnabledConsentOptoutGaUxEnabledReConsent() throws Exception {
        UiUtils.setAsRowDevice();
        UiUtils.disableGaUxFeature();
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false);
        UiUtils.consentConfirmationScreen(sContext, mDevice, false, false);

        mDevice.pressHome();
        UiUtils.restartAdservices();
        UiUtils.enableGaUxFeature();

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false);
    }

    /**
     * Verify that for EU devices with non zeroed-out AdId, notification displayed, and then GA UX
     * feature enabled, the EU GA UX notification is displayed as part of the re-consent
     * notification feature.
     */
    @Test
    public void testEuAdIdEnabledGaUxEnabledReconsent() throws Exception {
        UiUtils.setAsEuDevice();
        UiUtils.disableGaUxFeature();
        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);
        UiUtils.verifyNotification(sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
        UiUtils.consentConfirmationScreen(sContext, mDevice, false, true);

        mDevice.pressHome();
        UiUtils.restartAdservices();
        UiUtils.enableGaUxFeature();

        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatus();

        adServicesStatusResponse.get();

        UiUtils.verifyGaUxNotification(
                sContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true);
    }

    @Test
    public void testDeleteStatus() {
        UiUtils.clearSavedStatus();
        UiUtils.restartAdservices();
    }

    private ListenableFuture<Boolean> getAdservicesStatus() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.isAdServicesEnabled(
                            CALLBACK_EXECUTOR,
                            new OutcomeReceiver<Boolean, Exception>() {
                                @Override
                                public void onResult(Boolean result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }
}
