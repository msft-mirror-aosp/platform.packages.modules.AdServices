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
package com.android.adservices.tests.ui.gaux.alreadyenrolledchannel;

import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants.UX;
import com.android.adservices.tests.ui.libs.UiUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@ScreenRecordRule.ScreenRecord
@SetFlagDisabled(KEY_IS_EEA_DEVICE)
public class ExtGaUxAlreadyEnrolledChannelRowTest
        extends AdExtServicesGaUxAlreadyEnrolledChannelTestCase {

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

        // Reset consent and thereby AdServices data before each test.
        UiUtils.refreshConsentResetToken(flags);

        SettableFuture<Boolean> responseFuture = SettableFuture.create();

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
        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");
    }

    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed.
     */
    @Test
    public void testGaRowAdIdEnabled() throws Exception {
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.GA_UX, true);

        // Notifications should not be shown twice.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX, true);
    }

    /**
     * Verify that for GA, ROW devices with zeroed-out AdId, the GA EU notification is displayed.
     */
    @Test
    public void testGaRowAdIdDisabled() throws Exception {
        UiUtils.setAsRowDevice(flags);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ true, UX.GA_UX, true);

        // Notifications should not be shown twice.
        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ false, /* isEuTest */ true, UX.GA_UX, true);
    }
}
