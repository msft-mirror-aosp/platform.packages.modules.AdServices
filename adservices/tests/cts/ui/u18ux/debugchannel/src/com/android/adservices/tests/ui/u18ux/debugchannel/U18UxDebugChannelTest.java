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

package com.android.adservices.tests.ui.u18ux.debugchannel;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** CTS test for U18 users */
public class U18UxDebugChannelTest {

    private AdServicesCommonManager mCommonManager;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private UiDevice mDevice;
    private OutcomeReceiver<Boolean, Exception> mCallback;
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Before
    public void setUp() throws Exception {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(AdservicesTestHelper.isDeviceSupported());

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiUtils.enableNotificationPermission();

        mCommonManager = AdServicesCommonManager.get(sContext);

        UiUtils.enableConsentDebugMode();
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
        if (!AdservicesTestHelper.isDeviceSupported()) return;

        mDevice.pressHome();
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testEntrypointDisabled() throws Exception {
        UiUtils.enableU18();
        UiUtils.enableGa();
        boolean entryPointEnabled = false;
        boolean isU18Account = true, isAdult = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdult)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothTrueAdIdEnabled() throws Exception {
        UiUtils.enableU18();
        UiUtils.enableGa();
        boolean entryPointEnabled = true;
        boolean isU18Account = true, isAdult = true;
        boolean adIdEnabled = true;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdult)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18TrueAdultFalseAdIdEnabled() throws Exception {
        UiUtils.enableU18();
        UiUtils.enableGa();
        boolean entryPointEnabled = true;
        boolean isU18Account = true, isAdult = false;
        boolean adIdEnabled = true;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdult)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothTrueAdIdDisabled() throws Exception {
        UiUtils.enableU18();
        UiUtils.enableGa();
        boolean entryPointEnabled = true;
        boolean isU18Account = true, isAdult = true;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdult)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18TrueAdultFalseAdIdDisabled() throws Exception {
        UiUtils.enableU18();
        UiUtils.enableGa();
        boolean entryPointEnabled = true;
        boolean isU18Account = true, isAdult = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdult)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothFalseAdIdDisabled() throws Exception {
        UiUtils.enableU18();
        UiUtils.enableGa();
        boolean entryPointEnabled = true;
        boolean isU18Account = false, isAdult = false;
        boolean adIdEnabled = false;
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(adIdEnabled)
                        .setAdultAccount(isAdult)
                        .setU18Account(isU18Account)
                        .setPrivacySandboxUiEnabled(entryPointEnabled)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        mCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        UiUtils.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }
}
