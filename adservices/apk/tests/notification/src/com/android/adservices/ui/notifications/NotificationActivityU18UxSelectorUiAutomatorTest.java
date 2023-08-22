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
package com.android.adservices.ui.notifications;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
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

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class NotificationActivityU18UxSelectorUiAutomatorTest {
    private static final String NOTIFICATION_PACKAGE = "android.adservices.ui.NOTIFICATIONS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static final int SCROLL_WAIT_TIME = 2000;
    private static final UiDevice sDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private String mTestName;

    @ClassRule
    public static final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @BeforeClass
    public static void classSetup() {
        AdservicesTestHelper.killAdservicesProcess(ApplicationProvider.getApplicationContext());
    }

    @Before
    public void setup() throws UiObjectNotFoundException, IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            CompatAdServicesTestUtils.setFlags();
        }
        ShellUtils.runShellCommand(
                "device_config put adservices enable_ad_services_system_api true");
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_activity_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices u18_ux_enabled true");
        ShellUtils.runShellCommand("device_config put adservices debug_ux U18_UX");

        sDevice.pressHome();
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() throws Exception {
        ApkTestUtil.takeScreenshot(sDevice, getClass().getSimpleName() + "_" + mTestName + "_");

        AdservicesTestHelper.killAdservicesProcess(ApplicationProvider.getApplicationContext());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            CompatAdServicesTestUtils.resetFlagsToDefault();
        }
        ShellUtils.runShellCommand("device_config put adservices u18_ux_enabled false");
    }

    @Test
    public void acceptFlowTest() throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        startActivity();

        UiObject u18NotificationTitle = getElement(R.string.notificationUI_u18_notification_title);
        assertThat(u18NotificationTitle.exists()).isTrue();

        clickMoreToBottom();

        UiObject
                leftControlButton =
                        getElement(R.string.notificationUI_u18_left_control_button_text),
                rightControlButton =
                        getElement(R.string.notificationUI_u18_right_control_button_text);
        assertThat(leftControlButton.exists()).isTrue();
        rightControlButton.click();
        assertThat(u18NotificationTitle.exists()).isFalse();
    }

    @Test
    public void clickSettingsTest() throws UiObjectNotFoundException, InterruptedException {
        mTestName = new Object() {}.getClass().getEnclosingMethod().getName();

        startActivity();

        clickMoreToBottom();

        UiObject
                leftControlButton =
                        getElement(R.string.notificationUI_u18_left_control_button_text),
                rightControlButton =
                        getElement(R.string.notificationUI_u18_right_control_button_text);
        assertThat(rightControlButton.exists()).isTrue();

        leftControlButton.click();

        // make sure it goes to u18 page rather than GA page
        UiObject topicTitle = getElement(R.string.settingsUI_topics_ga_title);
        assertThat((topicTitle.exists())).isFalse();
        UiObject measurementTitle = getElement(R.string.settingsUI_u18_measurement_view_title);
        ApkTestUtil.scrollTo(sDevice, R.string.settingsUI_u18_measurement_view_title);
        assertThat(measurementTitle.exists()).isTrue();
    }

    private void startActivity() {
        ShellUtils.runShellCommand(
                "device_config put adservices consent_notification_activity_debug_mode true");
        ShellUtils.runShellCommand("device_config put adservices debug_ux U18_UX");

        String notificationPackage = NOTIFICATION_PACKAGE;
        Intent intent = new Intent(notificationPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ApplicationProvider.getApplicationContext().startActivity(intent);
        sDevice.wait(Until.hasObject(By.pkg(notificationPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    private void clickMoreToBottom() throws UiObjectNotFoundException, InterruptedException {
        UiObject moreButton = getElement(R.string.notificationUI_more_button_text);
        if (!moreButton.exists()) {
            LogUtil.e("More Button not Found");
            return;
        }

        int clickCount = 10;
        while (moreButton.exists() && clickCount-- > 0) {
            moreButton.click();
            Thread.sleep(SCROLL_WAIT_TIME);
        }
        assertThat(moreButton.exists()).isFalse();
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }
}
