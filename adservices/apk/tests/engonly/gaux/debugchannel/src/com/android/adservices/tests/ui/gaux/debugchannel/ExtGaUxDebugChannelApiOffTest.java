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
package com.android.adservices.tests.ui.gaux.debugchannel;

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;

/** Test for verifying user consent notification trigger behaviors. */
@ScreenRecordRule.ScreenRecord
@SetFlagDisabled(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagEnabled(KEY_IS_EEA_DEVICE)
public final class ExtGaUxDebugChannelApiOffTest extends AdExtServicesGaUxDebugChannelTestCase {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        mTestName = getTestName();

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiUtils.enableNotificationPermission();

        mCommonManager = AdServicesCommonManager.get(sContext);

        // consent debug mode is turned on for this test class as we only care about the
        // first trigger (API call).
        UiUtils.enableConsentDebugMode(flags);
        UiUtils.disableNotificationFlowV2(flags);
        UiUtils.disableOtaStrings(flags);

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        mDevice.pressHome();
    }

    /** Verify that the API returns false when API is disabled. */
    @Test
    public void testApiDisabled() throws Exception {
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

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.GA_UX,
                true);
    }
}
