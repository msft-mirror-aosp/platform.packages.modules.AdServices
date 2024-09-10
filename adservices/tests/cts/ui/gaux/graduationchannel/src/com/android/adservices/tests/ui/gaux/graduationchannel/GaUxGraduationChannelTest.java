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
package com.android.adservices.tests.ui.gaux.graduationchannel;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
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
public final class GaUxGraduationChannelTest extends AdServicesGaUxGraduationChannelTestCase {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        mTestName = getTestName();

        UiUtils.setBinderTimeout(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);
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

        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed.
     */
    @Test
    public void testRowU18ToGaAdIdEnabled() throws Exception {
        UiUtils.setAsRowDevice(flags);
        UiUtils.enableU18(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates u18States =
                new AdServicesStates.Builder()
                        .setU18Account(true)
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(u18States, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.U18_UX);

        UiUtils.enableGa(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // No notifications should be shown as graduation channel is disabled.
        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.GA_UX);
    }

    /**
     * Verify that for beta, ROW devices with non zeroed-out AdId, the beta ROW notification is
     * displayed.
     */
    @Test
    public void testRowU18ToBetaAdIdEnabled() throws Exception {
        UiUtils.setAsRowDevice(flags);
        UiUtils.enableU18(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates u18States =
                new AdServicesStates.Builder()
                        .setU18Account(true)
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(u18States, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ true, /* isEuTest */ false, UX.U18_UX);

        UiUtils.enableBeta(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);
        AdServicesStates adultStates =
                new AdServicesStates.Builder()
                        .setU18Account(false)
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(adultStates, Executors.newCachedThreadPool(), mCallback);

        // No notifications should be shown as there is no enrollment channel from U18 to Beta UX.
        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, /* isDisplayed */ false, /* isEuTest */ false, UX.BETA_UX);
    }
}
