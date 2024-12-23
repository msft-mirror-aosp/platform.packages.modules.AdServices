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

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.ui.util.ApkTestUtil.getString;
import static com.android.adservices.ui.util.NotificationActivityTestUtil.WINDOW_LAUNCH_TIMEOUT;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.ui.util.AdservicesNotificationUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;

import org.junit.Test;

@SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagTrue(KEY_U18_UX_ENABLED)
@SetFlagFalse(KEY_PAS_UX_ENABLED)
@SetFlagTrue(KEY_ADSERVICES_ENABLED)
@SetFlagTrue(KEY_ENABLE_BACK_COMPAT)
@SetStringFlag(name = KEY_DEBUG_UX, value = "GA_UX")
public final class NotificationActivityGAV2UxSelectorUiAutomatorTest
        extends AdservicesNotificationUiTestCase {

    @Test
    public void euAcceptFlowTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ true, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_confirmation_left_control_button_text);
        ApkTestUtil.assertNotNull(
                leftControlButton, R.string.notificationUI_confirmation_left_control_button_text);
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_confirmation_right_control_button_text);
        ApkTestUtil.assertNotNull(
                rightControlButton, R.string.notificationUI_confirmation_right_control_button_text);

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
        assertWithMessage("EEA 2nd page title should not be null").that(title2).isNotNull();

        // Retrieve new instances to avoid android.support.test.uiautomator.StaleObjectException.
        leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_confirmation_left_control_button_text);
        assertWithMessage("left button should not there on page top")
                .that(leftControlButton)
                .isNull();
        rightControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_confirmation_right_control_button_text);
        assertWithMessage("right button should not there on page top")
                .that(rightControlButton)
                .isNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_left_control_button_text_eu);
        ApkTestUtil.assertNotNull(
                leftControlButton, R.string.notificationUI_left_control_button_text_eu);
        rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_right_control_button_ga_text_eu_v2);
        ApkTestUtil.assertNotNull(
                rightControlButton, R.string.notificationUI_right_control_button_ga_text_eu_v2);

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
        assertWithMessage("After EEA second page, there should no page exist")
                .that(title2)
                .isNull();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void rowClickSettingsTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        ApkTestUtil.assertNotNull(
                leftControlButton, R.string.notificationUI_left_control_button_text);
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        ApkTestUtil.assertNotNull(
                rightControlButton, R.string.notificationUI_right_control_button_text);

        leftControlButton.click();
        mDevice.wait(
                Until.gone(By.text(getString(R.string.notificationUI_left_control_button_text))),
                WINDOW_LAUNCH_TIMEOUT);
        UiObject2 topicsTitle =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_topics_ga_title);
        assertWithMessage(
                        "notification enter settings should see topics title %s ",
                        getString(R.string.settingsUI_topics_ga_title))
                .that(topicsTitle)
                .isNotNull();
        UiObject2 appsTitle = ApkTestUtil.getElement(mDevice, R.string.settingsUI_apps_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_apps_ga_title);
        assertWithMessage(
                        "notification enter settings should see apps title %s ",
                        getString(R.string.settingsUI_apps_ga_title))
                .that(appsTitle)
                .isNotNull();
    }
}
