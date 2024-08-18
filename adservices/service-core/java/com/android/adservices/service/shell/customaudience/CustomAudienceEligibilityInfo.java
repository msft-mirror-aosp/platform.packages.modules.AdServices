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

package com.android.adservices.service.shell.customaudience;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.adselection.AuctionServerCustomAudienceFilterer;

import com.google.auto.value.AutoValue;

import java.util.List;

/**
 * Contains eligibility status for a custom audience in an auction. TODO(b/359543771): Add reasons
 * for ineligibility.
 */
@AutoValue
public abstract class CustomAudienceEligibilityInfo {

    /**
     * @return true if the CA is eligible for an on-device auction.
     */
    public abstract boolean isEligibleForOnDeviceAuction();

    /**
     * @return true if the CA is eligible for a server auction.
     */
    public abstract boolean isEligibleForServerAuction();

    /**
     * Construct a {@link CustomAudienceEligibilityInfo} instance.
     *
     * @param isEligibleForOnDeviceAuction if the CA is eligible for an on-device auction.
     * @param isEligibleForServerAuction if the CA is eligible for a server auction.
     * @return a new instance of {@link CustomAudienceEligibilityInfo}
     */
    public static CustomAudienceEligibilityInfo create(
            boolean isEligibleForOnDeviceAuction, boolean isEligibleForServerAuction) {
        return new AutoValue_CustomAudienceEligibilityInfo(
                isEligibleForOnDeviceAuction, isEligibleForServerAuction);
    }

    /**
     * Construct a {@link CustomAudienceEligibilityInfo} instance directly from CA data.
     *
     * @param customAudience The custom audience to check eligibility for.
     * @param activeCustomAudiences List of active custom audience from the DB.
     * @return a new instance of {@link CustomAudienceEligibilityInfo}
     */
    public static CustomAudienceEligibilityInfo create(
            DBCustomAudience customAudience, List<DBCustomAudience> activeCustomAudiences) {
        return create(
                activeCustomAudiences.contains(customAudience),
                AuctionServerCustomAudienceFilterer.isValidCustomAudienceForServerSideAuction(
                        customAudience));
    }
}
