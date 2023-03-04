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

package com.android.adservices.common;

import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.common.DBAdData;

import java.util.List;
import java.util.stream.Collectors;

public class DBAdDataFixture {
    public static List<DBAdData> getValidDbAdDataListByBuyer(AdTechIdentifier buyer) {
        return AdDataFixture.getValidFilterAdsByBuyer(buyer).stream()
                .map(DBAdData::fromServiceObject)
                .collect(Collectors.toList());
    }

    public static List<DBAdData> getValidDbAdDataListByBuyerNoFilters(AdTechIdentifier buyer) {
        return AdDataFixture.getValidAdsByBuyer(buyer).stream()
                .map(DBAdData::fromServiceObject)
                .collect(Collectors.toList());
    }

    public static List<DBAdData> getInvalidDbAdDataListByBuyer(AdTechIdentifier buyer) {
        return AdDataFixture.getInvalidAdsByBuyer(buyer).stream()
                .map(DBAdData::fromServiceObject)
                .collect(Collectors.toList());
    }
}
