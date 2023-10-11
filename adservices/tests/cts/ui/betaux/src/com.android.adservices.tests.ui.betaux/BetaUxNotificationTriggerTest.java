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
package com.android.adservices.tests.ui.betaux;

import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_DISABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.AD_ID_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_DISABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.ENTRY_POINT_ENABLED;

import android.adservices.common.AdServicesCommonManager;
import android.content.Context;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for verifying user consent notification trigger behaviors. */
@RunWith(AndroidJUnit4.class)
@ScreenRecordRule.ScreenRecord
public class BetaUxNotificationTriggerTest {

    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

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
        UiUtils.disableNotificationFlowV2();
        UiUtils.disableOtaStrings();

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        if (!AdservicesTestHelper.isDeviceSupported()) return;

        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        mDevice.pressHome();

        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    /** Verify no notification is displayed when the entry point is disabled for ROW devices. */
    @Test
    @FlakyTest(bugId = 297119850)
    public void testBetaRowEntryPointDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_DISABLED, AD_ID_ENABLED);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice,
                /* isDisplayed */ false,
                /* isEuTest */ true,
                /* isGa */ UiConstants.UX.BETA_UX);
    }

    /** Verify that for ROW devices with zeroed-out AdId, the EU notification is displayed. */
    @Test
    @FlakyTest(bugId = 297119850)
    public void testBetaRowAdIdDisabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_DISABLED);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ true,
                /* isGa */ UiConstants.UX.BETA_UX);
    }

    /** Verify that for ROW devices with non zeroed-out AdId, the ROW notification is displayed. */
    @Test
    @FlakyTest(bugId = 297119850)
    public void testBetaRowAdIdEnabled() throws Exception {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        UiUtils.setAsRowDevice();
        UiUtils.enableBeta();

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCommonManager.setAdServicesEnabled(ENTRY_POINT_ENABLED, AD_ID_ENABLED);

        AdservicesWorkflows.verifyNotification(
                sContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                /* isGa */ UiConstants.UX.BETA_UX);
    }
}