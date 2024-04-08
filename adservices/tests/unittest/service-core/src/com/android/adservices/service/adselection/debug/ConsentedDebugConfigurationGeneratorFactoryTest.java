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

import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ConsentedDebugConfigurationGeneratorFactoryTest extends AdServicesMockitoTestCase {
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();
    private static final Instant CREATION_TIMESTAMP = FIXED_NOW;
    private static final Duration EXPIRY_DURATION = Duration.ofDays(1);
    private static final Instant EXPIRY_TIMESTAMP = CREATION_TIMESTAMP.plus(EXPIRY_DURATION);
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

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        Optional<ConsentedDebugConfiguration> actual =
                mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration();

        Truth.assertThat(actual.isPresent()).isTrue();
        Truth.assertThat(actual.get()).isEqualTo(expected);
        Mockito.verify(mConsentedDebugConfigurationDao)
                .getAllActiveConsentedDebugConfigurations(
                        Mockito.any(Instant.class), Mockito.anyInt());
    }

    @Test
    public void testSetConsentedDebugConfiguration_doesNotPopulateWhenNoEntryInDatabase() {
        boolean isConsented = true;
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(List.of());

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        Optional<ConsentedDebugConfiguration> actual =
                mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration();

        Truth.assertThat(actual.isPresent()).isFalse();
        Mockito.verify(mConsentedDebugConfigurationDao)
                .getAllActiveConsentedDebugConfigurations(
                        Mockito.any(Instant.class), Mockito.anyInt());
    }

    @Test
    public void testSetConsentedDebugConfiguration_doesNotPopulateWhenListIsNull() {
        boolean isConsented = true;
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(null);

        mConsentedDebugConfigurationGenerator =
                getConsentedDebugConfigurationGenerator(isConsented);
        Optional<ConsentedDebugConfiguration> actual =
                mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration();

        Truth.assertThat(actual.isPresent()).isFalse();
        Mockito.verify(mConsentedDebugConfigurationDao)
                .getAllActiveConsentedDebugConfigurations(
                        Mockito.any(Instant.class), Mockito.anyInt());
    }

    @Test
    public void testSetConsentedDebugConfiguration_doesNotPopulateWhenDisabled() {

        mConsentedDebugConfigurationGenerator = getConsentedDebugConfigurationGenerator(false);
        Optional<ConsentedDebugConfiguration> actual =
                mConsentedDebugConfigurationGenerator.getConsentedDebugConfiguration();

        Truth.assertThat(actual.isPresent()).isFalse();
        Mockito.verify(mConsentedDebugConfigurationDao, Mockito.never())
                .getAllActiveConsentedDebugConfigurations(
                        Mockito.any(Instant.class), Mockito.anyInt());
    }

    private ConsentedDebugConfigurationGenerator getConsentedDebugConfigurationGenerator(
            boolean consent) {
        ConsentedDebugConfigurationGeneratorFactory consentedDebugConfigurationGeneratorFactory =
                new ConsentedDebugConfigurationGeneratorFactory(
                        consent, mConsentedDebugConfigurationDao);
        return consentedDebugConfigurationGeneratorFactory.create();
    }
}
