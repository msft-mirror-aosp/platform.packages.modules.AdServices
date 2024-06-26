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

package com.android.adservices.service.adselection;

import static android.adservices.adselection.AdSelectionConfigFixture.BUYER_1;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;

public class BuyerInputDataFetcherBuyerAllowListImplTest extends AdServicesMockitoTestCase {

    @Mock CustomAudienceDao mCustomAudienceDaoMock;

    @Mock EncodedPayloadDao mEncodedPayloadDaoMock;

    private BuyerInputDataFetcher mBuyerInputDataFetcher;

    private static final List<AdTechIdentifier> BUYERS_LIST = List.of(BUYER_1);

    @Before
    public void setup() {
        mBuyerInputDataFetcher =
                new BuyerInputDataFetcherBuyerAllowListImpl(
                        mCustomAudienceDaoMock, mEncodedPayloadDaoMock);
    }

    @Test
    public void testGetActiveCustomAudiences() {
        Instant now = Instant.now();
        long activeWindowMs = 10;

        mBuyerInputDataFetcher.getActiveCustomAudiences(BUYERS_LIST, now, activeWindowMs);

        ExtendedMockito.verify(mCustomAudienceDaoMock)
                .getActiveCustomAudienceByBuyers(BUYERS_LIST, now, activeWindowMs);
        ExtendedMockito.verifyNoMoreInteractions(mCustomAudienceDaoMock);
    }

    @Test
    public void testGetProtectedAudienceSignals() {
        mBuyerInputDataFetcher.getProtectedAudienceSignals(BUYERS_LIST);

        ExtendedMockito.verify(mEncodedPayloadDaoMock).getAllEncodedPayloadsForBuyers(BUYERS_LIST);
        ExtendedMockito.verifyNoMoreInteractions(mEncodedPayloadDaoMock);
    }
}
