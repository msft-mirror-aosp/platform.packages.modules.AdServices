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
import android.annotation.NonNull;

import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Holds filters to remove ads from the selectAds auction. */
public final class AdFiltererImpl implements AdFilterer {

    @NonNull private final AppInstallDao mAppInstallDao;

    public AdFiltererImpl(@NonNull AppInstallDao appInstallDao) {
        Objects.requireNonNull(appInstallDao);
        mAppInstallDao = appInstallDao;
    }

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
    @Override
    public List<DBCustomAudience> filterCustomAudiences(List<DBCustomAudience> cas) {
        List<DBCustomAudience> toReturn = new ArrayList<>();
        for (DBCustomAudience ca : cas) {
            List<DBAdData> filteredAds = new ArrayList<>();
            for (DBAdData ad : ca.getAds()) {
                if (shouldAdBeFiltered(ad, ca.getBuyer())) {
                    filteredAds.add(ad);
                }
            }
            if (!filteredAds.isEmpty()) {
                toReturn.add(new DBCustomAudience.Builder(ca).setAds(filteredAds).build());
            }
        }
        return toReturn;
    }

    /**
     * Takes a list of ads, and returns a new list with the ads that should not be in the auction
     * removed.
     *
     * <p>Note that DBAdData objects are shallow copied to the new list.
     *
     * @param ads The list of ads to filter.
     * @param buyer The buyer adtech who is trying to display the ad.
     * @return A list of ads identical to the ads input, but with any ads that should be filtered
     *     removed.
     */
    @Override
    public List<DBAdData> filterContextualAds(List<DBAdData> ads, AdTechIdentifier buyer) {
        List<DBAdData> toReturn = new ArrayList<>();
        for (DBAdData ad : ads) {
            if (shouldAdBeFiltered(ad, buyer)) {
                toReturn.add(ad);
            }
        }
        return toReturn;
    }

    private boolean shouldAdBeFiltered(DBAdData ad, AdTechIdentifier buyer) {
        if (ad.getAdFilters() == null) {
            return true;
        }
        return shouldAppInstallAdBeFiltered(ad, buyer);
    }

    private boolean shouldAppInstallAdBeFiltered(DBAdData ad, AdTechIdentifier buyer) {
        /* This could potentially be optimized by grouping the ads by package name before running
         * the queries, but unless the DB cache is playing poorly with these queries there might
         * not be a major performance improvement.
         */
        if (ad.getAdFilters().getAppInstallFilters() == null) {
            return true;
        }
        for (String packageName : ad.getAdFilters().getAppInstallFilters().getPackageNames()) {
            if (mAppInstallDao.canBuyerFilterPackage(buyer, packageName)) {
                return false;
            }
        }
        return true;
    }
}
