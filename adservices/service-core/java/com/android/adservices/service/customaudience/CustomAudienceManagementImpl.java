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

package com.android.adservices.service.customaudience;

import android.adservices.customaudience.CustomAudience;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.data.AdServicesDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Worker for implementation of {@link CustomAudienceManagementServiceImpl}.
 *
 * <p>This class is thread safe.
 */
public class CustomAudienceManagementImpl {
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static CustomAudienceManagementImpl sSingleton;

    private final CustomAudienceDao mCustomAudienceDao;
    private final Clock mClock;

    @VisibleForTesting
    CustomAudienceManagementImpl(@NonNull CustomAudienceDao customAudienceDao,
            @NonNull Clock clock) {
        mCustomAudienceDao = customAudienceDao;
        mClock = clock;
    }

    /**
     * Gets an instance of {@link CustomAudienceManagementImpl} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    public static CustomAudienceManagementImpl getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = new CustomAudienceManagementImpl(
                        AdServicesDatabase.getInstance(context).customAudienceDao(),
                        Clock.systemUTC());
            }
            return sSingleton;
        }
    }

    /**
     * Perform check on {@link CustomAudience} and write into db if it is valid.
     *
     * @param customAudience instance staged to be inserted.
     */
    public void joinCustomAudience(@NonNull CustomAudience customAudience) {
        Objects.requireNonNull(customAudience);
        Instant currentTime = mClock.instant();

        DBCustomAudience dbCustomAudience =
                DBCustomAudience.fromServiceObject(customAudience, "not.implemented.yet",
                        currentTime);

        mCustomAudienceDao.insertOrOverrideCustomAudience(dbCustomAudience);
    }

    /**
     * Delete a custom audience with given key. No-op if not exist.
     */
    public void leaveCustomAudience(@NonNull String owner, @NonNull String buyer,
            @NonNull String name) {
        Preconditions.checkStringNotEmpty(owner);
        Preconditions.checkStringNotEmpty(buyer);
        Preconditions.checkStringNotEmpty(name);

        mCustomAudienceDao.deleteCustomAudienceByPrimaryKey(owner, buyer, name);
    }
}
