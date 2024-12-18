/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.ui.util;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_TOGGLE_SPEED_BUMP_ENABLED;
import static com.android.adservices.ui.util.AdServicesUiTestCase.LAUNCH_TIMEOUT;
import static com.android.adservices.ui.util.ApkTestUtil.assertNotNull;
import static com.android.adservices.ui.util.ApkTestUtil.getConsentSwitch;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;

import java.util.concurrent.TimeUnit;

/** Util class for Settings tests. */
public final class SettingsTestUtil {

    private static final String TAG = SettingsTestUtil.class.getSimpleName();
    private static final int WINDOW_LAUNCH_TIMEOUT = 2_000;
    private static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS = 2_000;
    private static final String PRIVACY_SANDBOX_UI = "android.adservices.ui.SETTINGS";

    public static void settingsRemoveMainToggleAndMeasurementEntryTestUtil(UiDevice device) {
        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);

        // make sure we are on the main settings page
        UiObject2 appButton = ApkTestUtil.scrollTo(device, R.string.settingsUI_apps_ga_title);
        assertNotNull(appButton, R.string.settingsUI_apps_ga_title);

        UiObject2 topicsButton = ApkTestUtil.scrollTo(device, R.string.settingsUI_topics_ga_title);
        assertNotNull(topicsButton, R.string.settingsUI_topics_ga_title);

        // click measurement page
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_title);

        // verify have entered to measurement page
        UiObject2 measurementSwitch =
                ApkTestUtil.getElement(device, R.string.settingsUI_measurement_switch_title);
        assertNotNull(measurementSwitch, R.string.settingsUI_measurement_switch_title);

        pressBack(device);
        // verify back to the main page
        assertNotNull(appButton, R.string.settingsUI_apps_ga_title);
    }

    public static void measurementDialogTestUtil(UiDevice device, AdServicesFlagsSetterRule flags)
            throws RemoteException {
        flags.setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, true);
        device.setOrientationNatural();
        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        // open measurement view
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_title);

        // click reset
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_reset_title);
        UiObject2 resetButton =
                ApkTestUtil.getElement(device, R.string.settingsUI_measurement_view_reset_title);
        assertNotNull(resetButton, R.string.settingsUI_measurement_view_reset_title);

        // click reset again
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_reset_title);
        resetButton =
                ApkTestUtil.getElement(device, R.string.settingsUI_measurement_view_reset_title);
        assertNotNull(resetButton, R.string.settingsUI_measurement_view_reset_title);
    }

    public static void topicsToggleTestUtil(UiDevice device, AdServicesFlagsSetterRule flags)
            throws RemoteException {
        flags.setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        // 1) disable Topics API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_topics_ga_title);

        UiObject2 topicsToggle = getConsentSwitch(device);
        if (topicsToggle.isChecked()) {
            topicsToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ false);
        pressBack(device);

        // 2) enable Topics API
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_topics_ga_title);

        topicsToggle = getConsentSwitch(device);
        ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ false);
        topicsToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ true);
        pressBack(device);

        // 3) check if Topics API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_topics_ga_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        topicsToggle = getConsentSwitch(device);
        ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ true);
        pressBack(device);
    }

    public static void fledgeToggleTestUtil(UiDevice device, AdServicesFlagsSetterRule flags)
            throws RemoteException {
        flags.setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        // 1) disable Fledge API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);

        UiObject2 fledgeToggle = getConsentSwitch(device);
        if (fledgeToggle.isChecked()) {
            fledgeToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        ApkTestUtil.assertToggleState(fledgeToggle, /* checked= */ false);
        pressBack(device);

        // 2) enable Fledge API
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);

        fledgeToggle = getConsentSwitch(device);
        ApkTestUtil.assertToggleState(fledgeToggle, /* checked= */ false);
        fledgeToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        ApkTestUtil.assertToggleState(fledgeToggle, /* checked= */ true);
        pressBack(device);

        // 3) check if Fledge API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        fledgeToggle = getConsentSwitch(device);
        ApkTestUtil.assertToggleState(fledgeToggle, /* checked= */ true);
        pressBack(device);
    }

    public static void measurementToggleTestUtil(UiDevice device, AdServicesFlagsSetterRule flags)
            throws RemoteException {
        flags.setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        // 1) disable Measurement API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_title);

        UiObject2 measurementToggle = getConsentSwitch(device);
        if (measurementToggle.isChecked()) {
            measurementToggle.clickAndWait(
                    Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ false);
        pressBack(device);

        // 2) enable Measurement API
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_title);

        measurementToggle = getConsentSwitch(device);
        ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ false);
        measurementToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ true);
        pressBack(device);

        // 3) check if Measurement API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_view_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        measurementToggle = getConsentSwitch(device);
        ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ true);
        pressBack(device);
    }

    public static void topicsSubtitleTestUtil(UiDevice device, AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, false)
                .setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        SettingsTestUtil.checkSubtitleMatchesToggle(
                device,
                ".*:id/topics_preference_subtitle",
                R.string.settingsUI_topics_ga_title);
    }

    public static void appsSubtitleTestUtil(UiDevice device, AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, false)
                .setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        SettingsTestUtil.checkSubtitleMatchesToggle(
                device,
                ".*:id/apps_preference_subtitle",
                R.string.settingsUI_apps_ga_title);
    }

    public static void measurementSubtitleTestUtil(
            UiDevice device, AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_UI_DIALOGS_FEATURE_ENABLED, false)
                .setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        SettingsTestUtil.checkSubtitleMatchesToggle(
                device,
                ".*:id/measurement_preference_subtitle",
                R.string.settingsUI_measurement_view_title);
    }

    public static void topicsToggleDialogTestUtil(
            UiDevice device, AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, true);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_topics_ga_title);

        UiObject2 topicsToggle = getConsentSwitch(device);
        if (topicsToggle.isChecked()) {
            // turn it off
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_topics_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(dialogOptOutTitle, R.string.settingsUI_dialog_topics_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ false);
            // then turn it on again
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_topics_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(dialogOptInTitle, R.string.settingsUI_dialog_topics_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ true);
        } else {
            // turn it on
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_topics_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(dialogOptInTitle, R.string.settingsUI_dialog_topics_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ true);
            // then turn it off
            topicsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_topics_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(dialogOptOutTitle, R.string.settingsUI_dialog_topics_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            topicsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(topicsToggle, /* checked= */ false);
        }
    }

    public static void appsToggleDialogTestUtil(UiDevice device, AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, true);
        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);

        UiObject2 appsToggle = getConsentSwitch(device);
        if (appsToggle.isChecked()) {
            // turn it off
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_apps_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(dialogOptOutTitle, R.string.settingsUI_dialog_apps_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(appsToggle, /* checked= */ false);
            // then turn it on again
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_apps_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(dialogOptInTitle, R.string.settingsUI_dialog_apps_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(appsToggle, /* checked= */ true);
        } else {
            // turn it on
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_apps_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(dialogOptInTitle, R.string.settingsUI_dialog_apps_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(appsToggle, /* checked= */ true);
            // then turn it off
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_apps_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(dialogOptOutTitle, R.string.settingsUI_dialog_apps_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            appsToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(appsToggle, /* checked= */ false);
        }
    }

    public static void measurementToggleDialogTestUtil(
            UiDevice device, AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, true);
        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);

        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_measurement_ga_title);

        UiObject2 measurementToggle = getConsentSwitch(device);

        if (measurementToggle.isChecked()) {
            // turn it off
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_measurement_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(dialogOptOutTitle, R.string.settingsUI_dialog_measurement_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ false);
            // then turn it on again
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_measurement_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(dialogOptInTitle, R.string.settingsUI_dialog_measurement_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ true);
        } else {
            // turn it on
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptInTitle =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_measurement_opt_in_title);
            UiObject2 okButton =
                    ApkTestUtil.getElement(device, R.string.settingsUI_dialog_acknowledge);
            assertNotNull(dialogOptInTitle, R.string.settingsUI_dialog_measurement_opt_in_title);
            okButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ true);
            // then turn it off
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            UiObject2 dialogOptOutTitle =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_measurement_opt_out_title);
            UiObject2 positiveButton =
                    ApkTestUtil.getElement(
                            device, R.string.settingsUI_dialog_opt_out_positive_text);
            assertNotNull(dialogOptOutTitle, R.string.settingsUI_dialog_measurement_opt_out_title);
            positiveButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            // Retrieve new instance to avoid android.support.test.uiautomator.StaleObjectException.
            measurementToggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(measurementToggle, /* checked= */ false);
        }
    }

    /**
     * Tests whether the new PAS Fledge view has updated PAS text.
     *
     * @param device UiDevice
     * @param flags AdServicesFlagsSetterRule used for setting the flags
     * @throws RemoteException during screen rotation
     */
    public static void fledgeViewTextPasEnabledTest(
            UiDevice device, AdServicesFlagsSetterRule flags) throws RemoteException {
        flags.setFlag(KEY_PAS_UX_ENABLED, true)
                .setFlag(KEY_IS_EEA_DEVICE_FEATURE_ENABLED, true)
                .setFlag(KEY_IS_EEA_DEVICE, false)
                .setFlag(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED, false)
                .setDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, true);

        ApkTestUtil.launchSettingView(device, LAUNCH_TIMEOUT);
        // 1) disable Fledge API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);
        device.waitForIdle();

        UiObject2 fledgeToggle = getConsentSwitch(device);
        if (fledgeToggle.isChecked()) {
            fledgeToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        }
        assertWithMessage("fledge toggle checked").that(fledgeToggle.isChecked()).isFalse();
        device.pressBack();

        // 2) enable Fledge API
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);

        fledgeToggle = getConsentSwitch(device);
        assertWithMessage("fledge toggle checked").that(fledgeToggle.isChecked()).isFalse();
        fledgeToggle.clickAndWait(Until.newWindow(), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        fledgeToggle = getConsentSwitch(device);
        assertWithMessage("fledge toggle checked").that(fledgeToggle.isChecked()).isTrue();
        device.pressBack();

        // 3) check if Fledge API is enabled
        ApkTestUtil.scrollToAndClick(device, R.string.settingsUI_apps_ga_title);
        // rotate device to test rotating as well
        device.setOrientationLeft();
        device.setOrientationNatural();
        fledgeToggle = getConsentSwitch(device);
        assertWithMessage("fledge toggle checked").that(fledgeToggle.isChecked()).isTrue();

        // 4) check text is PAS text
        UiObject2 bodyText =
                ApkTestUtil.getElement(device, R.string.settingsUI_pas_apps_view_body_text);
        assertNotNull(bodyText, R.string.settingsUI_pas_apps_view_body_text);
        device.pressBack();
    }

    public static void checkSubtitleMatchesToggle(
            UiDevice device, String regexResId, int stringIdOfTitle) {
        UiObject2 subtitle = ApkTestUtil.scrollTo(device, regexResId);
        if (subtitle.getText()
                .equals(ApkTestUtil.getString(R.string.settingsUI_subtitle_consent_off))) {
            ApkTestUtil.scrollToAndClick(device, stringIdOfTitle);
            UiObject2 toggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(toggle, /* checked= */ false);
            toggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            pressBack(device);
            subtitle = ApkTestUtil.scrollTo(device, regexResId);
            assertWithMessage("subtitle equals")
                    .that(
                            subtitle.getText()
                                    .equals(
                                            ApkTestUtil.getString(
                                                    R.string.settingsUI_subtitle_consent_off)))
                    .isFalse();
        } else {
            ApkTestUtil.scrollToAndClick(device, stringIdOfTitle);
            UiObject2 toggle = getConsentSwitch(device);
            ApkTestUtil.assertToggleState(toggle, /* checked= */ true);
            toggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT);
            pressBack(device);
            subtitle = ApkTestUtil.scrollTo(device, regexResId);
            assertWithMessage("subtitle equals")
                    .that(
                            subtitle.getText()
                                    .equals(
                                            ApkTestUtil.getString(
                                                    R.string.settingsUI_subtitle_consent_off)))
                    .isTrue();
        }
    }

    /** Presses the Back button. */
    public static void pressBack(UiDevice device) {
        Log.d(TAG, "pressBack()");
        device.pressBack();
    }

    /** Check if intent has package and activity installed. */
    public static boolean isSettingsIntentInstalled() {
        Intent intent = ApkTestUtil.getIntent(PRIVACY_SANDBOX_UI);
        return ApkTestUtil.isIntentInstalled(intent);
    }

    /** check component enabled and wait for it before tests start */
    public static void setupBeforeTests() throws InterruptedException {
        // Check intent component enabled, if not, sleep for 1 min for bootCompleteReceiver to get
        // invoked on S
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !isSettingsIntentInstalled()) {
            TimeUnit.SECONDS.sleep(60);
        }
    }
}
