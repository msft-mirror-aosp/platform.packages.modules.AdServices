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

import static android.adservices.adselection.AuctionEncryptionKeyFixture.COORDINATOR_URL_AUCTION_URI;

import static com.android.adservices.service.common.CoordinatorOriginUriValidator.VALIDATOR_NO_OP;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.common.CoordinatorOriginUriValidator;

import org.junit.Before;
import org.junit.Test;

public class ServerAuctionTestCoordinatorUriStrategyTest extends AdServicesMockitoTestCase {
    private ServerAuctionTestCoordinatorUriStrategy mStrategy;

    @Before
    public void setup() {
        mStrategy = new ServerAuctionTestCoordinatorUriStrategy();
    }

    @Test
    public void testGetCoordinatorOriginUriValidator_returnsCorrectValidator() {
        CoordinatorOriginUriValidator result = mStrategy.getCoordinatorOriginUriValidator();

        assertThat(result).isEqualTo(VALIDATOR_NO_OP);
    }

    @Test
    public void testGetAuctionEncryptionKeyFetchUri_returnsUri() {
        Uri result = mStrategy.getAuctionEncryptionKeyFetchUri(COORDINATOR_URL_AUCTION_URI);

        assertThat(result).isEqualTo(COORDINATOR_URL_AUCTION_URI);
    }

    @Test
    public void testGetAuctionEncryptionKeyFetchUri_NullUri_returnsNull() {
        Uri result = mStrategy.getAuctionEncryptionKeyFetchUri(null);

        assertThat(result).isNull();
    }

    @Test
    public void testGetAuctionEncryptionKeyFetchUri_EmptyUri_returnsEmptyUri() {
        Uri result = mStrategy.getAuctionEncryptionKeyFetchUri(Uri.EMPTY);

        assertThat(result).isEqualTo(Uri.EMPTY);
    }
}
