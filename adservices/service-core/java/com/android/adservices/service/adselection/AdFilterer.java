/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;

import java.util.List;

/** Interface for filtering ads out of an ad selection auction. */
public interface AdFilterer {
    /**
     * Takes a list of CAs and returns an identical list with any ads that should be filtered
     * removed.
     *
     * <p>Note that some of the copying to the new list is shallow, so the original list should not
     * be re-used after the method is called.
     *
     * @param cas A list of CAs to filter ads for.
     * @return A list of cas identical to the cas input, but with any ads that should be filtered
     *     removed.
     */
    List<DBCustomAudience> filterCustomAudiences(List<DBCustomAudience> cas);
    /**
     * Determines if the given contextual ad should be removed from the auction.
     *
     * @param ad The ad to evaluate.
     * @param buyer The buyer adtech who is trying to display the ad.
     * @return True if ad should be kept and false otherwise.
     */
    List<DBAdData> filterContextualAds(List<DBAdData> ad, AdTechIdentifier buyer);
}
