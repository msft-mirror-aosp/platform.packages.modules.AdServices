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

package com.android.adservices.service.adselection.encryption;

import static android.adservices.adselection.AuctionEncryptionKeyFixture.ALLOWLIST;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION_2;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION_2_URI;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION_URI;

import static com.android.adservices.service.devapi.DevContext.UNKNOWN_APP_BECAUSE_DEVICE_DEV_OPTIONS_IS_DISABLED;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevSession;
import com.android.adservices.service.devapi.DevSessionState;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ServerAuctionCoordinatorUriStrategyFactoryTest extends AdServicesMockitoTestCase {

    ServerAuctionCoordinatorUriStrategyFactory mServerAuctionCoordinatorUriStrategyFactory;

    private static final DevSession DEV_SESSION_SERVER_AUCTION_TEST_KEYS_ENABLED =
            DevSession.builder()
                    .setState(DevSessionState.IN_DEV)
                    .setServerAuctionTestKeysEnabled(true)
                    .build();

    private static final DevSession DEV_SESSION_PROD =
            DevSession.builder().setState(DevSessionState.IN_PROD).build();

    private static final DevContext DEV_CONTEXT_PROD =
            DevContext.builder(UNKNOWN_APP_BECAUSE_DEVICE_DEV_OPTIONS_IS_DISABLED)
                    .setDeviceDevOptionsEnabled(false)
                    .setDevSession(DEV_SESSION_PROD)
                    .build();

    private static final DevContext DEV_CONTEXT_SERVER_AUCTION_TEST_KEYS_ENABLED =
            DevContext.builder(ApplicationContextSingleton.get().getPackageName())
                    .setDeviceDevOptionsEnabled(true)
                    .setDevSession(DEV_SESSION_SERVER_AUCTION_TEST_KEYS_ENABLED)
                    .build();

    @Before
    public void setup() {
        mServerAuctionCoordinatorUriStrategyFactory =
                new ServerAuctionCoordinatorUriStrategyFactory(ALLOWLIST);
    }

    @Test
    public void testCreateStrategy_ServerAuctionTestKeysEnabled_ReturnsTestStrategy() {
        ServerAuctionCoordinatorUriStrategy strategy =
                mServerAuctionCoordinatorUriStrategyFactory.createStrategy(
                        DEV_CONTEXT_SERVER_AUCTION_TEST_KEYS_ENABLED);

        assertThat(strategy).isInstanceOf(ServerAuctionTestCoordinatorUriStrategy.class);
    }

    @Test
    public void testCreateStrategy_ServerAuctionTestKeysDisabled_ReturnsProdStrategy() {
        ServerAuctionCoordinatorUriStrategy strategy =
                mServerAuctionCoordinatorUriStrategyFactory.createStrategy(DEV_CONTEXT_PROD);

        assertThat(strategy).isInstanceOf(ServerAuctionProdCoordinatorUriStrategy.class);
    }

    @Test
    public void getListOfUrisFromCommaSeparatedAllowlist() {
        List<Uri> result = mServerAuctionCoordinatorUriStrategyFactory
                .getListOfUrisFromCommaSeparatedAllowlist();

        assertThat(result).containsExactly(COORDINATOR_URL_AUCTION_URI, COORDINATOR_URL_AUCTION_2_URI);
    }
}
