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

package com.android.adservices.ui;


import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import java.util.stream.Stream;

/** Utility class for notification related logic. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxUtil {

    private static final String INTENT_EXTRA_EEA_DEVICE_ARGUMENT = "isEUDevice";

    /** Returns whether the device is an EEA device. */
    public static boolean isEeaDevice(FragmentActivity fragmentActivity) {
        boolean debugEnabled = DebugFlags.getInstance().getConsentNotificationActivityDebugMode();
        boolean stateManagerIsEeaDevice = UxStatesManager.getInstance().isEeaDevice();
        LoggerFactory.getUILogger()
                .d("uxutil get notification activity debug mode as %b", debugEnabled);
        LoggerFactory.getUILogger()
                .d("state manager get is eea device as %b", stateManagerIsEeaDevice);
        if (debugEnabled) {
            boolean intentIsEeaDevice =
                    fragmentActivity
                            .getIntent()
                            .getBooleanExtra(
                                    INTENT_EXTRA_EEA_DEVICE_ARGUMENT, stateManagerIsEeaDevice);
            LoggerFactory.getUILogger().d("get intent is eea device as %b", intentIsEeaDevice);
            return intentIsEeaDevice;
        } else {
            boolean deviceIsEeaDevice =
                    !ConsentManager.getInstance().isAdIdEnabled() || stateManagerIsEeaDevice;
            LoggerFactory.getUILogger().d("get device is eea device as %b", deviceIsEeaDevice);
            return deviceIsEeaDevice;
        }
    }

    /** Returns if UXStates should be used. */
    public static boolean isUxStatesReady() {
        PrivacySandboxUxCollection ux = getUx();
        return FlagsFactory.getFlags().getEnableAdServicesSystemApi()
                && ux != null
                && ux != PrivacySandboxUxCollection.UNSUPPORTED_UX;
    }

    /** Returns the current UX. */
    public static PrivacySandboxUxCollection getUx() {
        if (DebugFlags.getInstance().getConsentNotificationActivityDebugMode()) {
            return Stream.of(PrivacySandboxUxCollection.values())
                    .filter(ux -> ux.toString().equals(FlagsFactory.getFlags().getDebugUx()))
                    .findFirst()
                    .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX);
        } else {
            return UxStatesManager.getInstance().getUx();
        }
    }

    /** Returns the specified UX flag. */
    public static boolean getFlag(String uxFlagKey) {
        return UxStatesManager.getInstance().getFlag(uxFlagKey);
    }

    /**
     * PAS could be enabled but user may not have received notification, so user would see GA UX
     * instead of PAS UX. Before the notification card is shown the notification has not been
     * displayed yet, so if the calling context is related to this then we should only look at the
     * PAS UX flag.
     *
     * @param beforeNotificationShown True if the calling context is logic before PAS notification
     *     shown has been recorded.
     * @return True if user will see PAS UX for settings/notification, otherwise false.
     */
    public static boolean pasUxIsActive(boolean beforeNotificationShown) {
        return UxStatesManager.getInstance().pasUxIsActive(beforeNotificationShown);
    }
}
