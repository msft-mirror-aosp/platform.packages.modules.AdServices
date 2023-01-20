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

package com.android.server.adservices;

import android.annotation.NonNull;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.consent.AppConsentManager;
import com.android.server.adservices.consent.ConsentManager;

import java.io.IOException;
import java.util.Map;

/**
 * Manager to handle User Instance. This is to ensure that each user profile is isolated.
 *
 * @hide
 */
public class UserInstanceManager {

    // We have 1 ConsentManager per user/user profile. This is to isolate user's data.
    @GuardedBy("UserInstanceManager.class")
    private final Map<Integer, ConsentManager> mConsentManagerMapLocked = new ArrayMap<>();

    @GuardedBy("UserInstanceManager.class")
    private final Map<Integer, AppConsentManager> mAppConsentManagerMapLocked = new ArrayMap<>();

    private final String mAdServicesBaseDir;

    UserInstanceManager(String adServicesBaseDir) {
        mAdServicesBaseDir = adServicesBaseDir;
    }

    @NonNull
    ConsentManager getOrCreateUserConsentManagerInstance(int userIdentifier) throws IOException {
        synchronized (UserInstanceManager.class) {
            ConsentManager instance = mConsentManagerMapLocked.get(userIdentifier);
            if (instance == null) {
                instance = ConsentManager.createConsentManager(mAdServicesBaseDir, userIdentifier);
                mConsentManagerMapLocked.put(userIdentifier, instance);
            }
            return instance;
        }
    }

    @NonNull
    AppConsentManager getOrCreateUserAppConsentManagerInstance(int userIdentifier)
            throws IOException {
        synchronized (UserInstanceManager.class) {
            AppConsentManager instance = mAppConsentManagerMapLocked.get(userIdentifier);
            if (instance == null) {
                instance =
                        AppConsentManager.createAppConsentManager(
                                mAdServicesBaseDir, userIdentifier);
                mAppConsentManagerMapLocked.put(userIdentifier, instance);
            }
            return instance;
        }
    }

    @VisibleForTesting
    void tearDownForTesting() {
        synchronized (UserInstanceManager.class) {
            for (ConsentManager consentManager : mConsentManagerMapLocked.values()) {
                consentManager.tearDownForTesting();
            }
            for (AppConsentManager appConsentManager : mAppConsentManagerMapLocked.values()) {
                appConsentManager.tearDownForTesting();
            }
        }
    }
}
