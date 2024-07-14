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

package com.android.adservices.tests.ui.u18ux.detentionchannel;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** CTS test for U18 users */
public final class U18UxDetentionChannelTest
        extends AdServicesU18UxDetentionChannelCtsRootTestCase {

    private AdServicesCommonManager mCommonManager;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private UiDevice mDevice;
    private OutcomeReceiver<Boolean, Exception> mCallback;
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() throws Exception {
        UiUtils.setBinderTimeout(flags);
        AdservicesTestHelper.killAdservicesProcess(sContext);
        UiUtils.resetAdServicesConsentData(sContext, flags);
        UiUtils.enableNotificationPermission();
        UiUtils.enableGa(flags);
        UiUtils.disableNotificationFlowV2(flags);
        UiUtils.disableOtaStrings(flags);
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mCommonManager = AdServicesCommonManager.get(sContext);

        UiUtils.enableU18(flags);

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

        UiUtils.restartAdservices();
    }

    @After
    public void tearDown() throws Exception {
        mDevice.pressHome();

        UiUtils.restartAdservices();
    }

    @Test
    public void testGaUxU18DetentionChannel() throws Exception {
        UiUtils.enableGa(flags);
        UiUtils.setAsRowDevice(flags);

        AdServicesStates adServicesAdultStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(false)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesAdultStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice,
                /* isDisplayed= */ true,
                /* isEuTest= */ false,
                UiConstants.UX.GA_UX);

        AdServicesStates adServicesU18States =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(false)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesU18States, CALLBACK_EXECUTOR, mCallback);

        // Verify no U18 UX notification can be triggered.
        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice,
                /* isDisplayed= */ false,
                /* isEuTest= */ false,
                UiConstants.UX.U18_UX);
    }
}
