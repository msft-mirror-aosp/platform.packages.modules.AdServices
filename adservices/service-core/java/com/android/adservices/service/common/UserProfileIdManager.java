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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.UserProfileIdDao;
import com.android.adservices.data.common.UserProfileIdDaoSharedPreferencesImpl;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.UUID;

/** Manager of user profile id. */
public class UserProfileIdManager {
    private static final LoggerFactory.Logger LOGGER = LoggerFactory.getFledgeLogger();

    private static final Object SINGLETON_LOCK = new Object();

    private final UserProfileIdDao mUserProfileIdDao;

    @GuardedBy("SINGLETON_LOCK")
    private static volatile UserProfileIdManager sUserProfileIdManager;

    @VisibleForTesting
    UserProfileIdManager(@NonNull final UserProfileIdDao userProfileIdDao) {
        Objects.requireNonNull(userProfileIdDao);

        mUserProfileIdDao = userProfileIdDao;
    }

    /** Returns the singleton instance of the {@link UserProfileIdManager} */
    public static UserProfileIdManager getInstance(@NonNull final Context context) {
        Objects.requireNonNull(context, "Context must be provided.");
        synchronized (SINGLETON_LOCK) {
            if (sUserProfileIdManager == null) {
                sUserProfileIdManager =
                        new UserProfileIdManager(
                                UserProfileIdDaoSharedPreferencesImpl.getInstance(context));
            }
            return sUserProfileIdManager;
        }
    }

    /**
     * Returns the user profile ID.
     *
     * <ol>
     *   <li>If user profile ID does not exist, create and persist a new ID;
     *   <li>Return the id in the storage.
     * </ol>
     */
    @Nullable
    public UUID getOrCreateId() {
        UUID id = mUserProfileIdDao.getUserProfileId();

        if (id != null) {
            return id;
        }

        LOGGER.v("User profile ID is not present, generating a new ID.");
        id = UUID.randomUUID();
        mUserProfileIdDao.setUserProfileId(id);
        return id;
    }

    /** Clear the user profile id storage in case of consent revoke. */
    public void deleteId() {
        mUserProfileIdDao.deleteStorage();
    }
}