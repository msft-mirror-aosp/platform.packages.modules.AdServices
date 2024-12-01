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

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.devapi.DevContext;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Worker for implementation of {@link CustomAudienceServiceImpl}.
 *
 * <p>This class is thread safe.
 */
public final class CustomAudienceImpl {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static CustomAudienceImpl sSingleton;

    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final CustomAudienceQuantityChecker mCustomAudienceQuantityChecker;
    @NonNull private final Validator<CustomAudience> mCustomAudienceValidator;
    @NonNull private final Clock mClock;
    @NonNull private final Flags mFlags;
    private final boolean mAuctionServerRequestFlagsEnabled;
    private final boolean mSellerConfigurationFlagEnabled;
    private final ComponentAdsStrategy mComponentAdsStrategy;

    @VisibleForTesting
    public CustomAudienceImpl(
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull CustomAudienceQuantityChecker customAudienceQuantityChecker,
            @NonNull Validator<CustomAudience> customAudienceValidator,
            @NonNull Clock clock,
            @NonNull Flags flags,
            ComponentAdsStrategy componentAdsStrategy) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(customAudienceQuantityChecker);
        Objects.requireNonNull(customAudienceValidator);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(flags);

        mCustomAudienceDao = customAudienceDao;
        mCustomAudienceQuantityChecker = customAudienceQuantityChecker;
        mCustomAudienceValidator = customAudienceValidator;
        mClock = clock;
        mFlags = flags;
        mAuctionServerRequestFlagsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAuctionServerRequestFlagsEnabled);
        mSellerConfigurationFlagEnabled =
                BinderFlagReader.readFlag(
                        flags::getFledgeGetAdSelectionDataSellerConfigurationEnabled);
        mComponentAdsStrategy = componentAdsStrategy;
    }

    /**
     * Gets an instance of {@link CustomAudienceImpl} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    public static CustomAudienceImpl getInstance() {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                Flags flags = FlagsFactory.getFlags();
                CustomAudienceDao customAudienceDao =
                        CustomAudienceDatabase.getInstance().customAudienceDao();
                sSingleton =
                        new CustomAudienceImpl(
                                customAudienceDao,
                                new CustomAudienceQuantityChecker(customAudienceDao, flags),
                                CustomAudienceValidator.getInstance(),
                                Clock.systemUTC(),
                                flags,
                                ComponentAdsStrategy.createInstance(
                                        /* componentAdsEnabled= */ false));
            }
            return sSingleton;
        }
    }

    /**
     * Perform check on {@link CustomAudience} and write into db if it is valid.
     *
     * @param customAudience instance staged to be inserted.
     * @param callerPackageName package name for the calling application, used as the owner
     *     application identifier
     */
    public void joinCustomAudience(
            @NonNull CustomAudience customAudience,
            @NonNull String callerPackageName,
            @NonNull DevContext devContext) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(callerPackageName);
        Instant currentTime = mClock.instant();
        sLogger.v("Requested CA to join: %s", customAudience);
        sLogger.v("Validating CA limits");
        mCustomAudienceQuantityChecker.check(customAudience, callerPackageName);
        sLogger.v("Validating CA");
        mCustomAudienceValidator.validate(customAudience);

        boolean frequencyCapFilteringEnabled = mFlags.getFledgeFrequencyCapFilteringEnabled();
        sLogger.v("Frequency cap filtering enabled flag is %s", frequencyCapFilteringEnabled);
        boolean appInstallFilteringEnabled = mFlags.getFledgeAppInstallFilteringEnabled();
        sLogger.v("App install filtering enabled flag is %s", appInstallFilteringEnabled);
        boolean adRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
        sLogger.v("Ad render id enabled flag is %s", adRenderIdEnabled);
        AdDataConversionStrategy dataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                        frequencyCapFilteringEnabled,
                        appInstallFilteringEnabled,
                        adRenderIdEnabled);

        boolean isDebuggableCustomAudience = devContext.getDeviceDevOptionsEnabled();
        sLogger.v("Is debuggable custom audience: %b", isDebuggableCustomAudience);

        Duration customAudienceDefaultExpireIn =
                Duration.ofMillis(mFlags.getFledgeCustomAudienceDefaultExpireInMs());

        // TODO (b/352602308) Add priority to fetchAndJoinCustomAudience() and
        // scheduleCustomAudienceUpdate()
        DBCustomAudience dbCustomAudience =
                DBCustomAudience.fromServiceObject(
                        customAudience,
                        callerPackageName,
                        currentTime,
                        customAudienceDefaultExpireIn,
                        dataConversionStrategy,
                        isDebuggableCustomAudience,
                        mAuctionServerRequestFlagsEnabled,
                        mSellerConfigurationFlagEnabled);

        sLogger.v("Inserting CA in the DB: %s", dbCustomAudience);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudience, customAudience.getDailyUpdateUri(), isDebuggableCustomAudience);
        mComponentAdsStrategy.persistComponentAds(
                customAudience, callerPackageName, mCustomAudienceDao);
    }

    /** Delete a custom audience with given key. No-op if not exist. */
    public void leaveCustomAudience(
            @NonNull String owner, @NonNull AdTechIdentifier buyer, @NonNull String name) {
        Preconditions.checkStringNotEmpty(owner);
        Objects.requireNonNull(buyer);
        Preconditions.checkStringNotEmpty(name);

        mCustomAudienceDao.deleteAllCustomAudienceDataByPrimaryKey(owner, buyer, name);
    }

    /** Returns DAO to be used in {@link CustomAudienceServiceImpl} */
    public CustomAudienceDao getCustomAudienceDao() {
        return mCustomAudienceDao;
    }
}
