/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.ui.settings;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class ConsentSettingsUiAutomatorTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;
    private MockitoSession mStaticMockSession;
    private PhFlags mPhFlags;
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(ApkTestUtil.isDeviceSupported());

        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startMockCompatFlags();
        } else {
            ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled false");
        }
    }

    @After
    public void teardown() {
        if (!ApkTestUtil.isDeviceSupported()) return;

        AdservicesTestHelper.killAdservicesProcess(CONTEXT);

        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void consentSystemServerOnlyTest() throws UiObjectNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());

        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    public void consentPpApiOnlyTest() throws UiObjectNotFoundException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            doReturn(1).when(mMockFlags).getConsentSourceOfTruth();
            doReturn(false).when(mPhFlags).getUIDialogsFeatureEnabled();
        } else {
            ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
            ShellUtils.runShellCommand(
                    "device_config put adservices ui_dialogs_feature_enabled false");
        }
        consentTest(false);
    }

    @Test
    public void consentSystemServerAndPpApiTest() throws UiObjectNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled false");
        consentTest(false);
    }

    @Test
    public void consentSystemServerOnlyDialogsOnTest() throws UiObjectNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 0");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    public void consentPpApiOnlyDialogsOnTest() throws UiObjectNotFoundException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            doReturn(1).when(mMockFlags).getConsentSourceOfTruth();
            doReturn(true).when(mPhFlags).getUIDialogsFeatureEnabled();
        } else {
            ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 1");
            ShellUtils.runShellCommand(
                    "device_config put adservices ui_dialogs_feature_enabled true");
        }
        consentTest(true);
    }

    @Test
    public void consentSystemServerAndPpApiDialogsOnTest() throws UiObjectNotFoundException {
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 2");
        ShellUtils.runShellCommand("device_config put adservices ui_dialogs_feature_enabled true");
        consentTest(true);
    }

    @Test
    public void consentAppSearchOnlyTest() throws UiObjectNotFoundException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            doReturn(3).when(mMockFlags).getConsentSourceOfTruth();
            doReturn(true).when(mPhFlags).getUIDialogsFeatureEnabled();
            consentTest(true);
        } else {
            ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth 3");
            ShellUtils.runShellCommand(
                    "device_config put adservices ui_dialogs_feature_enabled true");
            consentTest(true);
            ShellUtils.runShellCommand("device_config put adservices consent_source_of_truth null");
        }
    }

    private void consentTest(boolean dialogsOn) throws UiObjectNotFoundException {

        ApkTestUtil.launchSettingView(
                ApplicationProvider.getApplicationContext(), sDevice, LAUNCH_TIMEOUT);

        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isTrue();

        setConsentToFalse(dialogsOn);

        // click switch
        performSwitchClick(dialogsOn, mainSwitch);
        assertThat(mainSwitch.isChecked()).isTrue();

        // click switch
        performSwitchClick(dialogsOn, mainSwitch);
        assertThat(mainSwitch.isChecked()).isFalse();
    }

    private void setConsentToFalse(boolean dialogsOn) throws UiObjectNotFoundException {
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        if (mainSwitch.isChecked()) {
            performSwitchClick(dialogsOn, mainSwitch);
        }
    }

    private void performSwitchClick(boolean dialogsOn, UiObject mainSwitch)
            throws UiObjectNotFoundException {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject dialogTitle =
                    ApkTestUtil.getElement(sDevice, R.string.settingsUI_dialog_opt_out_title);
            UiObject positiveText =
                    ApkTestUtil.getElement(
                            sDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            assertThat(dialogTitle.exists()).isTrue();
            assertThat(positiveText.exists()).isTrue();
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }

    private void startMockCompatFlags() {
        // Static mocking
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(PhFlags.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doNothing()
                .when(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                BackgroundJobsManager.scheduleJobsPerApi(
                                        any(Context.class), any(AdServicesApiType.class)));
        mPhFlags = spy(PhFlags.getInstance());
        ExtendedMockito.doReturn(mPhFlags).when(PhFlags::getInstance);
        doReturn(false).when(mMockFlags).getGaUxFeatureEnabled();

        // Back compat only supports the following flags
        doReturn(1).when(mMockFlags).getBlockedTopicsSourceOfTruth();
        doReturn(true).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
    }
}
