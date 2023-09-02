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
package com.android.adservices.service.ui.data;

import static com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection.UNSUPPORTED_UX;

import android.adservices.common.AdServicesStates;
import android.annotation.NonNull;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.PhFlags;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import java.util.Map;

/**
 * Manager that deals with all UX related states. All other UX code should use this class to read ux
 * component states. Specifically, this class:
 * <li>Reads process statble UX flags from {@code Flags}, and provide these flags through the
 *     getFlags API.
 * <li>Reads process statble consent manager bits such as UX and enrollment channel, so that these
 *     values are process stable.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class UxStatesManager {

    private static final Object LOCK = new Object();
    private static volatile UxStatesManager sUxStatesManager;
    private final Map<String, Boolean> mUxFlags;
    private final ConsentManager mConsentManager;
    private final SharedPreferences mUxSharedPreferences;
    private PrivacySandboxUxCollection mUx;
    private PrivacySandboxEnrollmentChannelCollection mEnrollmentChannel;
    private final boolean mIsEeaDevice;

    UxStatesManager(
            @NonNull Context context,
            @NonNull Flags flags,
            @NonNull ConsentManager consentManager) {
        mUxFlags = flags.getUxFlags();
        mConsentManager = consentManager;
        mIsEeaDevice = DeviceRegionProvider.isEuDevice(context);
        mUxSharedPreferences =
                context.getSharedPreferences("UX_SHARED_PREFERENCES", Context.MODE_PRIVATE);
    }

    /** Returns an instance of the UxStatesManager. */
    @NonNull
    public static UxStatesManager getInstance(Context context) {
        if (sUxStatesManager == null) {
            synchronized (LOCK) {
                if (sUxStatesManager == null) {
                    sUxStatesManager =
                            new UxStatesManager(
                                    context,
                                    FlagsFactory.getFlags(),
                                    ConsentManager.getInstance(context));
                }
            }
        }
        return sUxStatesManager;
    }

    /** Saves the AdServices states into data stores. */
    public void persistAdServicesStates(AdServicesStates adServicesStates) {
        // Only a subset of states should be persisted.
        mConsentManager.setAdIdEnabled(adServicesStates.isAdIdEnabled());
        mConsentManager.setU18Account(adServicesStates.isU18Account());
        mConsentManager.setAdultAccount(adServicesStates.isAdultAccount());
        mConsentManager.setEntryPointEnabled(adServicesStates.isPrivacySandboxUiEnabled());
    }

    /** Returns process statble UX flags. */
    public boolean getFlag(String uxFlagKey) {
        if (!mUxFlags.containsKey(uxFlagKey)) {
            LogUtil.e("Key not found in cached UX flags: ", uxFlagKey);
        }
        Boolean value = mUxFlags.get(uxFlagKey);
        return value != null ? value : false;
    }

    /** Returns process statble UX. */
    public PrivacySandboxUxCollection getUx() {
        // Lazy read.
        if (mUx == null) {
            mUx = mConsentManager.getUx();
        }
        return mUx != null ? mUx : UNSUPPORTED_UX;
    }

    /** Returns process statble enrollment channel. */
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel() {
        // Lazy read.
        if (mEnrollmentChannel == null) {
            mEnrollmentChannel = mConsentManager.getEnrollmentChannel(mUx);
        }
        return mEnrollmentChannel;
    }

    /** Returns process statble devicce region. */
    public boolean isEeaDevice() {
        return mIsEeaDevice;
    }

    /** Returns a common shared preference for storing temporary UX states. */
    public SharedPreferences getUxSharedPreferences() {
        return mUxSharedPreferences;
    }

    /**
     * Returns whether the user is already enrolled for the current UX. or it is supervised account,
     * we then set ux and default measurement consent.
     */
    public boolean isEnrolledUser(Context context) {
        boolean isNotificationDisplayed =
                mConsentManager.wasGaUxNotificationDisplayed()
                        || mConsentManager.wasU18NotificationDisplayed()
                        || mConsentManager.wasNotificationDisplayed();
        // We follow the Chrome's capabilities practice here, when user is not in adult account and
        // u18 account, (the u18 account is for teen and un-supervised account), we are consider
        // them as supervised accounts for now, it actually also contains robot account, but we
        // don't have a capability for that, we will update this when we have the new capability.
        // TODO: when new capability is available, update with new capability.
        boolean isSupervisedAccountEnabled =
                getFlag(PhFlags.KEY_IS_U18_SUPERVISED_ACCOUNT_ENABLED);
        boolean isSupervisedUser =
                !mConsentManager.isU18Account() && !mConsentManager.isAdultAccount();
        // In case supervised account logging in second time and not able to set the ux to u18
        if (isSupervisedAccountEnabled && isSupervisedUser) {
            LogUtil.d("supervised user get");
            mConsentManager.setUx(PrivacySandboxUxCollection.U18_UX);
        }
        if (!isNotificationDisplayed) {
            if (isSupervisedAccountEnabled && isSupervisedUser) {
                // We initial the default consent and notification.
                LogUtil.d("supervised user initial");
                mConsentManager.setU18NotificationDisplayed(true);
                mConsentManager.enable(context, AdServicesApiType.MEASUREMENTS);
                return true;
            }
            return false;
        }
        return true;
    }
}
