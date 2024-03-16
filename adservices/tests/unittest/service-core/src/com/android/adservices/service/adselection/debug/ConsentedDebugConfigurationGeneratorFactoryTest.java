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

import static android.adservices.common.CommonFixture.FIXED_NOW;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.DBConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ConsentedDebugConfiguration;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAuctionInput;

import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ConsentedDebugConfigurationGeneratorFactoryTest extends AdServicesMockitoTestCase {
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();
    private static final String EMPTY_DEBUG_TOKEN = "";
    private static final Instant CREATION_TIMESTAMP = FIXED_NOW;
    private static final Duration EXPIRY_DURATION = Duration.ofDays(1);
    private static final Instant EXPIRY_TIMESTAMP = CREATION_TIMESTAMP.plus(EXPIRY_DURATION);
    private static final ConsentedDebugConfiguration DISABLED_CONSENTED_DEBUG_CONFIGURATION =
            ConsentedDebugConfiguration.newBuilder()
                    .setIsConsented(false)
                    .setToken(EMPTY_DEBUG_TOKEN)
                    .setIsDebugInfoInResponse(false)
                    .build();
    @Mock private ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;

    private ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;

    @Test
    public void test_constructor() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> new ConsentedDebugConfigurationGeneratorFactory(false, null));
    }

    @Test
    public void test_setConsentedDebugConfiguration_success() {
        boolean isConsented = true;
        DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                DBConsentedDebugConfiguration.create(
                        null, true, DEBUG_TOKEN, CREATION_TIMESTAMP, EXPIRY_TIMESTAMP);
        ConsentedDebugConfiguration expected =
                ConsentedDebugConfiguration.newBuilder()
                        .setIsConsented(isConsented)
                        .setToken(DEBUG_TOKEN)
                        .setIsDebugInfoInResponse(false)
                        .build();
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(List.of(dbConsentedDebugConfiguration));
        ProtectedAuctionInput.Builder builder = ProtectedAuctionInput.newBuilder();

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        builder = mConsentedDebugConfigurationGenerator.setConsentedDebugConfiguration(builder);

        ProtectedAuctionInput protectedAuctionInput = builder.build();
        ConsentedDebugConfiguration actual = protectedAuctionInput.getConsentedDebugConfig();
        Truth.assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testSetConsentedDebugConfiguration_doesNotPopulateWhenNoEntryInDatabase() {
        boolean isConsented = true;
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(List.of());
        ProtectedAuctionInput.Builder builder = ProtectedAuctionInput.newBuilder();

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        builder = mConsentedDebugConfigurationGenerator.setConsentedDebugConfiguration(builder);

        ProtectedAuctionInput protectedAuctionInput = builder.build();
        ConsentedDebugConfiguration actual = protectedAuctionInput.getConsentedDebugConfig();
        Truth.assertThat(actual).isEqualTo(DISABLED_CONSENTED_DEBUG_CONFIGURATION);
    }

    @Test
    public void testSetConsentedDebugConfiguration_doesNotPopulateWhenListIsNull() {
        boolean isConsented = true;
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(null);
        ProtectedAuctionInput.Builder builder = ProtectedAuctionInput.newBuilder();

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        builder = mConsentedDebugConfigurationGenerator.setConsentedDebugConfiguration(builder);

        ProtectedAuctionInput protectedAuctionInput = builder.build();
        ConsentedDebugConfiguration actual = protectedAuctionInput.getConsentedDebugConfig();
        Truth.assertThat(actual).isEqualTo(DISABLED_CONSENTED_DEBUG_CONFIGURATION);
    }

    @Test
    public void testSetConsentedDebugConfiguration_doesNotPopulateWhenDisabled() {
        boolean isConsented = true;
        ProtectedAuctionInput.Builder builder = ProtectedAuctionInput.newBuilder();

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        builder = mConsentedDebugConfigurationGenerator.setConsentedDebugConfiguration(builder);

        ProtectedAuctionInput protectedAuctionInput = builder.build();
        ConsentedDebugConfiguration actual = protectedAuctionInput.getConsentedDebugConfig();
        Truth.assertThat(actual).isEqualTo(DISABLED_CONSENTED_DEBUG_CONFIGURATION);
    }

    private ConsentedDebugConfigurationGenerator getConsentedDebugConfigurationGenerator(
            boolean consent) {
        ConsentedDebugConfigurationGeneratorFactory consentedDebugConfigurationGeneratorFactory =
                new ConsentedDebugConfigurationGeneratorFactory(
                        consent, mConsentedDebugConfigurationDao);
        return consentedDebugConfigurationGeneratorFactory.create();
    }
}
