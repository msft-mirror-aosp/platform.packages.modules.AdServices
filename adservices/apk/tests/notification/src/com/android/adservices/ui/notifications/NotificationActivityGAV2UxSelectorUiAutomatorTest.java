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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.ui.util.ApkTestUtil.getString;
import static com.android.adservices.ui.util.NotificationActivityTestUtil.WINDOW_LAUNCH_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NotificationActivityGAV2UxSelectorUiAutomatorTest extends AdServicesUiTestCase {

    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setDebugFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE, true)
                    .setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true)
                    .setFlag(KEY_GA_UX_FEATURE_ENABLED, true)
                    .setFlag(KEY_U18_UX_ENABLED, true)
                    .setFlag(KEY_DEBUG_UX, "GA_UX")
                    // TODO(b/297085722): remove seFlag() below if/when all flags are cleared
                    .setFlag(KEY_IS_EEA_DEVICE_FEATURE_ENABLED, true);

    @BeforeClass
    public static void classSetup() throws Exception {
        NotificationActivityTestUtil.setupBeforeTests();
    }

    @Before
    public void setup() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
    }

    @Test
    // TODO(b/297085722): remove SetFlagFalse below if/when all flags are cleared
    @SetFlagFalse(KEY_IS_EEA_DEVICE_FEATURE_ENABLED)
    public void euAcceptFlowTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ true, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_confirmation_left_control_button_text);
        assertThat(leftControlButton).isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_confirmation_right_control_button_text);
        assertThat(rightControlButton).isNotNull();

        rightControlButton.click();
        mDevice.wait(
                Until.gone(
                        By.text(
                                getString(
                                        R.string
                                                .notificationUI_confirmation_right_control_button_text))),
                WINDOW_LAUNCH_TIMEOUT);

        UiObject2 title2 =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_header_ga_title_eu_v2);
        assertThat(title2).isNotNull();

        // Retrieve new instances to avoid android.support.test.uiautomator.StaleObjectException.
        leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_confirmation_left_control_button_text);
        assertThat(leftControlButton).isNull();
        rightControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_confirmation_right_control_button_text);
        assertThat(rightControlButton).isNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_left_control_button_text_eu);
        assertThat(leftControlButton).isNotNull();
        rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_right_control_button_ga_text_eu_v2);
        assertThat(rightControlButton).isNotNull();

        rightControlButton.click();
        mDevice.wait(
                Until.gone(
                        By.text(
                                getString(
                                        R.string
                                                .notificationUI_right_control_button_ga_text_eu_v2))),
                WINDOW_LAUNCH_TIMEOUT);

        // Retrieve a new instance to avoid android.support.test.uiautomator.StaleObjectException.
        title2 = ApkTestUtil.getElement(mDevice, R.string.notificationUI_header_ga_title_eu_v2);
        assertThat(title2).isNull();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void rowClickSettingsTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        assertThat(leftControlButton).isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        assertThat(rightControlButton).isNotNull();

        leftControlButton.click();
        mDevice.wait(
                Until.gone(By.text(getString(R.string.notificationUI_left_control_button_text))),
                WINDOW_LAUNCH_TIMEOUT);
        UiObject2 topicsTitle =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_topics_ga_title);
        assertThat(topicsTitle).isNotNull();
        UiObject2 appsTitle = ApkTestUtil.getElement(mDevice, R.string.settingsUI_apps_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_apps_ga_title);
        assertThat(appsTitle).isNotNull();
    }
}
