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
package com.android.adservices.tests.ui.gaux.rpostotachannel;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiConstants.UX;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@ScreenRecordRule.ScreenRecord
public final class GaUxRvcPostOTAChannelTest extends AdServicesRvcPostOTAChannelTestCase {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        mTestName = getTestName();

        UiUtils.resetAdServicesConsentData(mContext, flags);

        UiUtils.enableNotificationPermission();
        UiUtils.enableGa(flags);
        UiUtils.enableRvc(flags);
        UiUtils.enableRvcNotification(flags);
        UiUtils.disableNotificationFlowV2(flags);
        UiUtils.disableOtaStrings(flags);

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mCommonManager = AdServicesCommonManager.get(mContext);

        // General purpose callback used for expected success calls.
        mCallback =
                new OutcomeReceiver<>() {
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
        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    /**
     * Verify that the R Post OTA notification is displayed after 1st U18 notification if msmt is
     * opted in.
     */
    @Test
    public void testU18ToGAForRPostOTA_optInMsmt() throws Exception {
        UiUtils.setAsRowDevice(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        // R 18+ user receives 1st U18 notification
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.RVC_UX);

        // Mock user is ota from Rvc by enabling consent manager ota debug mode
        UiUtils.setConsentManagerOtaDebugMode(flags);
        // Enable consent manager debug mode to mock user opt-in msmt API
        UiUtils.setConsentManagerDebugMode(flags);
        // Disable RVC UX to mock user is not eligible RVC UX post OTA
        UiUtils.disableRvc(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // User receive 2nd GA notification post R OTA
        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.GA_UX);

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // Notifications should not be shown twice
        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
        mDevice.pressHome();

        // User should be able to open GA UX after notification
        UiUtils.resetConsentManagerDebugMode(flags);
        AdservicesWorkflows.testSettingsPageFlow(
                mContext,
                mDevice,
                flags,
                UiConstants.UX.GA_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
    }

    /**
     * Verify that the R Post OTA notification is not displayed after 1st U18 notification if msmt
     * is opted out.
     */
    @Test
    public void testU18ToGAForRPostOTA_optOutMsmt() throws Exception {
        UiUtils.setAsRowDevice(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        // R 18+ user receives 1st U18 notification
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.RVC_UX);

        // Open settings page and opt out msmt api
        AdservicesWorkflows.testSettingsPageFlow(
                mContext,
                mDevice,
                flags,
                UiConstants.UX.RVC_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
        mDevice.pressHome();

        // Mock user is ota from Rvc by enabling consent manager ota debug mode
        UiUtils.setConsentManagerOtaDebugMode(flags);
        // Disable RVC UX to mock user is not eligible RVC UX post OTA
        UiUtils.disableRvc(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
        mDevice.pressHome();

        // User should be able to open GA UX post OTA
        AdservicesWorkflows.testSettingsPageFlow(
                mContext,
                mDevice,
                flags,
                UiConstants.UX.GA_UX,
                /* isOptIn= */ true,
                /* isFlipConsent= */ true,
                /* assertOptIn= */ false);
    }
}
