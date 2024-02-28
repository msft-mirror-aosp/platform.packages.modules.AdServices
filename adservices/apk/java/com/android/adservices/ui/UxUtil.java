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

import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentActivity;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import java.util.stream.Stream;

/** Utility class for notification related logic. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxUtil {

    /** Returns whether the device is an EEA device. */
    public static boolean isEeaDevice(FragmentActivity fragmentActivity, Context context) {
        return FlagsFactory.getFlags().getConsentNotificationActivityDebugMode()
                ? fragmentActivity
                        .getIntent()
                        .getBooleanExtra(
                                  "isEUDevice",
                                  UxStatesManager.getInstance().isEeaDevice())
                : !ConsentManager.getInstance().isAdIdEnabled()
                        || UxStatesManager.getInstance().isEeaDevice();
    }

    /** Returns if UXStates should be used. */
    public static boolean isUxStatesReady(Context context) {
        PrivacySandboxUxCollection ux = getUx(context);
        return FlagsFactory.getFlags().getEnableAdServicesSystemApi()
                && ux != null
                && ux != PrivacySandboxUxCollection.UNSUPPORTED_UX;
    }

    /** Returns the current UX. */
    public static PrivacySandboxUxCollection getUx(Context context) {
        if (FlagsFactory.getFlags().getConsentNotificationActivityDebugMode()) {
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

    /** Returns if PAS notification was displayed. */
    public static boolean wasPasNotificationDisplayed() {
        if (getFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)) {
            return getFlag(KEY_PAS_UX_ENABLED);
        }
        return ConsentManager.getInstance().wasPasNotificationDisplayed();
    }
}
