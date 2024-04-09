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

package com.android.adservices.service.adselection.debug;

import android.annotation.NonNull;

import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.DBConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory class to provide implementation for {@link ConsentedDebugConfigurationGenerator} based on
 * feature flag.
 */
public class ConsentedDebugConfigurationGeneratorFactory {

    private final boolean mConsentedDebugConfigurationEnabled;
    private final ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;

    /**
     * default constructor to create a new instance of ConsentedDebugConfigurationGeneratorFactory.
     */
    public ConsentedDebugConfigurationGeneratorFactory(
            boolean consentedDebugConfigurationEnabled,
            @NonNull ConsentedDebugConfigurationDao consentedDebugConfigurationDao) {
        Objects.requireNonNull(consentedDebugConfigurationDao);

        mConsentedDebugConfigurationEnabled = consentedDebugConfigurationEnabled;
        mConsentedDebugConfigurationDao = consentedDebugConfigurationDao;
    }

    /**
     * @return an instance of {@link ConsentedDebugConfigurationGenerator}.
     */
    public ConsentedDebugConfigurationGenerator create() {
        if (mConsentedDebugConfigurationEnabled) {
            return () -> {
                List<DBConsentedDebugConfiguration> dbConsentedDebugConfigurationList =
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Instant.now(), 1);
                if (dbConsentedDebugConfigurationList != null
                        && !dbConsentedDebugConfigurationList.isEmpty()) {
                    DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                            dbConsentedDebugConfigurationList.get(0);
                    return Optional.of(
                            ConsentedDebugConfiguration.newBuilder()
                                    .setIsConsented(
                                            dbConsentedDebugConfiguration.getIsConsentProvided())
                                    .setToken(dbConsentedDebugConfiguration.getDebugToken())
                                    .setIsDebugInfoInResponse(false)
                                    .build());
                }
                return Optional.empty();
            };
        } else {
            return () -> Optional.empty();
        }
    }
}
