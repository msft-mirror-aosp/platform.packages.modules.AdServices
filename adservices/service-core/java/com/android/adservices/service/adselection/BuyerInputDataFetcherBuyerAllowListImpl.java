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

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.EncodedPayloadDao;

import java.time.Instant;
import java.util.List;

public class BuyerInputDataFetcherBuyerAllowListImpl implements BuyerInputDataFetcher {
    private final CustomAudienceDao mCustomAudienceDao;
    private final EncodedPayloadDao mEncodedPayloadDao;

    public BuyerInputDataFetcherBuyerAllowListImpl(
            CustomAudienceDao customAudienceDao, EncodedPayloadDao encodedPayloadDao) {
        mCustomAudienceDao = customAudienceDao;
        mEncodedPayloadDao = encodedPayloadDao;
    }

    @Override
    public List<DBCustomAudience> getActiveCustomAudiences(
            List<AdTechIdentifier> buyers, Instant currentTime, long activeWindowTimeMs) {
        return mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                buyers, currentTime, activeWindowTimeMs);
    }

    @Override
    public List<DBEncodedPayload> getProtectedAudienceSignals(List<AdTechIdentifier> buyers) {
        return mEncodedPayloadDao.getAllEncodedPayloadsForBuyers(buyers);
    }
}
