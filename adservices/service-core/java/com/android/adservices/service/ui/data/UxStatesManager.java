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

import android.adservices.common.AdServicesStates;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

import java.util.Map;

/**
 * Manager that deals with all UX related states. All other UX code should use this class to read ux
 * component states. Specifically, this class:
 * <li>Reads sessionized UX flags from {@code Flags}, and provide these flags through the getFlags
 *     API.
 * <li>Reads sessionized consent manager bits such as UX and enrollment channel, so that these
 *     values are process stable.
 */
@RequiresApi(Build.VERSION_CODES.S)
public class UxStatesManager {

    private static final Object LOCK = new Object();
    private static volatile UxStatesManager sUxStatesManager;
    private final Map<String, Boolean> mUxFlags;
    private final ConsentManager mConsentManager;
    private PrivacySandboxUxCollection mUx;
    private PrivacySandboxEnrollmentChannelCollection mEnrollmentChannel;

    UxStatesManager(@NonNull Flags flags, @NonNull ConsentManager consentManager) {
        mUxFlags = flags.getUxFlags();
        mConsentManager = consentManager;
    }

    /** Returns an instance of the UxStatesManager. */
    @NonNull
    public static UxStatesManager getInstance(Context context) {
        if (sUxStatesManager == null) {
            synchronized (LOCK) {
                if (sUxStatesManager == null) {
                    sUxStatesManager =
                            new UxStatesManager(
                                    FlagsFactory.getFlags(), ConsentManager.getInstance(context));
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

    /** Return the sessionized UX flags. */
    public boolean getFlag(String uxFlagKey) {
        Boolean value = mUxFlags.get(uxFlagKey);
        return value != null ? value : false;
    }

    /** Return the current UX. */
    public PrivacySandboxUxCollection getUx() {
        // Lazy read.
        if (mUx == null) {
            mUx = mConsentManager.getUx();
        }
        return mUx;
    }

    /** Return the current enrollment channel. */
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel() {
        // Lazy read.
        if (mEnrollmentChannel == null) {
            mEnrollmentChannel = mConsentManager.getEnrollmentChannel(mUx);
        }
        return mEnrollmentChannel;
    }
}
