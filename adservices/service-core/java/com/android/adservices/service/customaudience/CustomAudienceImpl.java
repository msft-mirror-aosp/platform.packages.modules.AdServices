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
import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker for implementation of {@link CustomAudienceServiceImpl}.
 *
 * <p>This class is thread safe.
 */
public class CustomAudienceImpl {
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static CustomAudienceImpl sSingleton;

    private final CustomAudienceDao mCustomAudienceDao;
    private final Clock mClock;

    @VisibleForTesting
    public CustomAudienceImpl(@NonNull CustomAudienceDao customAudienceDao, @NonNull Clock clock) {
        mCustomAudienceDao = customAudienceDao;
        mClock = clock;
    }

    /**
     * Gets an instance of {@link CustomAudienceImpl} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    public static CustomAudienceImpl getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new CustomAudienceImpl(
                                CustomAudienceDatabase.getInstance(context).customAudienceDao(),
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

        // TODO(b/231997523): Add JSON field validation.
        DBCustomAudience dbCustomAudience =
                DBCustomAudience.fromServiceObject(
                        customAudience, "not.implemented.yet", currentTime);

        mCustomAudienceDao.insertOrOverrideCustomAudience(dbCustomAudience);
    }

    /** Delete a custom audience with given key. No-op if not exist. */
    public void leaveCustomAudience(
            @Nullable String owner, @NonNull String buyer, @NonNull String name) {
        Preconditions.checkStringNotEmpty(buyer);
        Preconditions.checkStringNotEmpty(name);

        mCustomAudienceDao.deleteCustomAudienceByPrimaryKey(
                Optional.ofNullable(owner).orElse("not.implemented.yet"), buyer, name);
    }

    /** Returns DAO to be used in {@link CustomAudienceServiceImpl} */
    public CustomAudienceDao getCustomAudienceDao() {
        return mCustomAudienceDao;
    }
}
