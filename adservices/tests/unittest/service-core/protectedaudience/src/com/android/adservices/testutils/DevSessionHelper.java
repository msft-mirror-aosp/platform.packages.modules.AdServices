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

package com.android.adservices.testutils;

import static com.android.adservices.service.devapi.DevSessionControllerResult.NO_OP;
import static com.android.adservices.service.devapi.DevSessionControllerResult.SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.DatabaseClearer;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionControllerImpl;
import com.android.adservices.service.devapi.DevSessionControllerResult;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.devapi.DevSessionDataStoreFactory;
import com.android.adservices.service.devapi.DevSessionProtoDataStore;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A helper that manages the AdServices development session for tests. This rule ensures that a
 * dev session is started correctly and always ended after each test, providing a clean environment.
 *
 * <p>Usage:
 *
 * <pre>
 * public class MyTest {
 *     public DevSessionHelper devSessionHelper = new DevSessionHelper(...);
 *
 *     &#64;Test
 *     public void myTest() {
 *         devSessionHelper.startDevSession(); // Example
 *         // Your test logic here
 *     }
 * }
 * </pre>
 */
public class DevSessionHelper {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final long DEV_SESSION_TIMEOUT_SEC = 3L;

    private DevSessionController mDevSessionController;
    private final CustomAudienceDao mCustomAudienceDao;
    private final AppInstallDao mAppInstallDao;
    private final FrequencyCapDao mFrequencyCapDao;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private boolean mInitialized = false;
    private final DevSessionDataStore mDevSessionDataStore;
    private final Flags mFlags =
            new Flags() {
                @Override
                public boolean getDeveloperModeFeatureEnabled() {
                    return true;
                }
            };

    public DevSessionHelper(CustomAudienceDao customAudienceDao, AppInstallDao appInstallDao, FrequencyCapDao frequencyCapDao, ProtectedSignalsDao protectedSignalsDao) {
        this.mCustomAudienceDao = customAudienceDao;
        this.mAppInstallDao = appInstallDao;
        this.mFrequencyCapDao = frequencyCapDao;
        this.mProtectedSignalsDao = protectedSignalsDao;
        this.mDevSessionDataStore = DevSessionDataStoreFactory.get(/*developerModeFeatureEnabled=*/true);
    }

    /**
     * Start a dev session.
     *
     * <p>After this method is called, the database setters will no longer work.
     */
    public void startDevSession() {
        if (!mInitialized) {
            this.mDevSessionController =
                    new DevSessionControllerImpl(
                            new DatabaseClearer(
                                    mCustomAudienceDao,
                                    mAppInstallDao,
                                    new AdFilteringFeatureFactory(
                                                    mAppInstallDao, mFrequencyCapDao, mFlags)
                                            .getFrequencyCapDataClearer(),
                                    mProtectedSignalsDao,
                                    AdServicesExecutors.getBackgroundExecutor()),
                            mDevSessionDataStore,
                            AdServicesExecutors.getLightWeightExecutor());
        }
        mInitialized = true;
        try {
            assertThat(
                            mDevSessionController
                                    .startDevSession()
                                    .get(DEV_SESSION_TIMEOUT_SEC, TimeUnit.SECONDS))
                    .isEqualTo(SUCCESS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            sLogger.e(e, "Failed to startDevSession correctly");
            throw new RuntimeException(e);
        }
        sLogger.v("DevSessionRule: Completed startDevSession()");
    }

    /**
     * End a dev session.
     *
     * <p>This method is no-op if {@link DevSessionHelper#startDevSession()} has not already been
     * called before.
     */
    public void endDevSession() {
        DevSessionControllerResult result;
        try {
            result =
                    mDevSessionController
                            .endDevSession()
                            .get(DEV_SESSION_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            sLogger.e(e, "Failed to endDevSession correctly");
            throw new RuntimeException(e);
        }
        assertThat(result).isIn(List.of(SUCCESS, NO_OP));
        sLogger.v("DevSessionRule: Completed endDevSession() with result %s", result.name());
    }
}
