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
import android.os.OutcomeReceiver;
import android.platform.test.rule.ScreenRecordRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.UiUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** CTS test for U18 users */
@ScreenRecordRule.ScreenRecord
public final class U18UxDebugChannelTest extends AdServicesU18UxDebugChannelCtsRootTestCase {
    private static AdServicesCommonManager sCommonManager;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
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

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiUtils.enableNotificationPermission();
        UiUtils.disableNotificationFlowV2(flags);
        UiUtils.disableOtaStrings(flags);

        sCommonManager = AdServicesCommonManager.get(mContext);

        UiUtils.enableConsentDebugMode(flags);
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

        mDevice.pressHome();

        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    @Test
    public void testEntrypointDisabled() throws Exception {
        UiUtils.enableU18(flags);
        UiUtils.enableGa(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(false)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext,
                mDevice, /* isDisplayed */
                false, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothTrueAdIdEnabled() throws Exception {
        UiUtils.enableU18(flags);
        UiUtils.enableGa(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18TrueAdultFalseAdIdEnabled() throws Exception {
        UiUtils.enableU18(flags);
        UiUtils.enableGa(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(false)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothTrueAdIdDisabled() throws Exception {
        UiUtils.enableU18(flags);
        UiUtils.enableGa(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18TrueAdultFalseAdIdDisabled() throws Exception {
        UiUtils.enableU18(flags);
        UiUtils.enableGa(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext,
                mDevice, /* isDisplayed */
                true, /* isEuTest */
                false,
                UiConstants.UX.U18_UX);
    }

    @Test
    public void testU18AdultBothFalseAdIdDisabled() throws Exception {
        UiUtils.enableU18(flags);
        UiUtils.enableGa(flags);

        AdservicesTestHelper.killAdservicesProcess(mContext);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(false)
                        .setU18Account(false)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build();

        sCommonManager.enableAdServices(adServicesStates, CALLBACK_EXECUTOR, mCallback);

        AdservicesWorkflows.verifyNotification(
                mContext, mDevice, false, false, UiConstants.UX.U18_UX);
    }
}
