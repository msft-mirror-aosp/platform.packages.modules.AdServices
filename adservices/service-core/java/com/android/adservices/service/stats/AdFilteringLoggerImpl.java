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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CONTEXTUAL_ADS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_UNSET;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

public class AdFilteringLoggerImpl implements AdFilteringLogger {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    // Indicates whether a CA or contextual ads is being filtered
    @AdsRelevanceStatusUtils.FilterProcessType private final int mFilterProcessType;

    // Overview of filtering process (apply to both CAs and contextual ads)
    private int mStatusCode;
    private long mAdFilteringStartTimestamp;
    private long mAdFilteringEndTimestamp;
    private long mAppInstallStartTimestamp;
    private long mAppInstallEndTimestamp;
    private long mFrequencyCapStartTimestamp;
    private long mFrequencyCapEndTimestamp;
    private int mTotalNumOfAdsBeforeFiltering;

    // CA specific metrics
    private int mNumOfAdsFilteredOutOfBidding;
    private int mTotalNumOfCustomAudiencesBeforeFiltering;
    private int mNumOfCustomAudiencesFilteredOutOfBidding;

    // Contextual ads specific metrics
    private int mTotalNumOfContextualAdsBeforeFiltering;
    private int mNumOfContextualAdsFiltered;
    private int mNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures;
    private int mNumOfContextualAdsFilteredOutOfBiddingNoAds;

    // Storage metrics
    private int mNumOfAdCounterKeysInFcapFilters;
    private int mNumOfPackagesInAppInstallFilters;
    private int mNumOfDbOperations;

    @NonNull private final Clock mClock;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    public AdFilteringLoggerImpl(
            @AdsRelevanceStatusUtils.FilterProcessType int filterProcessType,
            @NonNull AdServicesLogger adServicesLogger) {
        this(filterProcessType, adServicesLogger, Clock.getInstance());
    }

    @VisibleForTesting
    public AdFilteringLoggerImpl(
            @AdsRelevanceStatusUtils.FilterProcessType int filterProcessType,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Clock clock) {
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(clock);

        this.mFilterProcessType = filterProcessType;
        this.mAdServicesLogger = adServicesLogger;
        this.mClock = clock;
        sLogger.v("%s starts.", this.getClass().getName());
    }

    /** Sets the status code */
    public void setStatusCode(int statusCode) {
        mStatusCode = statusCode;
    }

    /** Sets the start timestamp for filtering */
    public void setAdFilteringStartTimestamp() {
        if (mAdFilteringStartTimestamp > 0) {
            sLogger.w("Ad filtering start timestamp already set");
            return;
        }
        mAdFilteringStartTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Logged ad filtering start timestamp: %s", mAdFilteringStartTimestamp);
    }

    /** Sets the end timestamp for filtering */
    public void setAdFilteringEndTimestamp() {
        if (mAdFilteringEndTimestamp > 0) {
            sLogger.w("Ad filtering end timestamp already set");
            return;
        }
        if (mAdFilteringStartTimestamp == 0) {
            sLogger.w("Ad filtering start timestamp is missing");
            return;
        }
        mAdFilteringEndTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Logged ad filtering end timestamp: %s", mAdFilteringEndTimestamp);
    }

    /** Sets the start timestamp for app install filtering */
    public void setAppInstallStartTimestamp() {
        if (mAppInstallStartTimestamp > 0) {
            sLogger.w("App install filtering start timestamp already set");
            return;
        }
        mAppInstallStartTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Logged app install filtering start timestamp: %s", mAppInstallStartTimestamp);
    }

    /** Sets the end timestamp for app install */
    public void setAppInstallEndTimestamp() {
        if (mAppInstallEndTimestamp > 0) {
            sLogger.w("App install filtering end timestamp already set");
            return;
        }
        if (mAppInstallStartTimestamp == 0) {
            sLogger.w("App install filtering start timestamp is missing");
            return;
        }
        mAppInstallEndTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Logged app install filtering end timestamp: %s", mAppInstallEndTimestamp);
    }

    /** Sets the start timestamp for fcap */
    public void setFrequencyCapStartTimestamp() {
        if (mFrequencyCapStartTimestamp > 0) {
            sLogger.w("Fcap filtering start timestamp already set");
            return;
        }
        mFrequencyCapStartTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Logged fcap filtering start timestamp: %s", mFrequencyCapStartTimestamp);
    }

    /** Sets the end timestamp for fcap */
    public void setFrequencyCapEndTimestamp() {
        if (mFrequencyCapEndTimestamp > 0) {
            sLogger.w("Fcap filtering end timestamp already set");
            return;
        }
        if (mFrequencyCapStartTimestamp == 0) {
            sLogger.w("Fcap filtering start timestamp is missing");
            return;
        }
        mFrequencyCapEndTimestamp = getServiceElapsedTimestamp();
        sLogger.v("Logged fcap filtering end timestamp: %s", mFrequencyCapEndTimestamp);
    }

    /** Sets the number of ads before filtering */
    public void setTotalNumOfAdsBeforeFiltering(int totalNumOfAdsBeforeFiltering) {
        mTotalNumOfAdsBeforeFiltering = totalNumOfAdsBeforeFiltering;
    }

    /** Sets the number of ads filtered out of bidding */
    public void setNumOfAdsFilteredOutOfBidding(int numOfAdsFilteredOutOfBidding) {
        mNumOfAdsFilteredOutOfBidding = numOfAdsFilteredOutOfBidding;
    }

    /** Sets the total number of custom audience before filtering */
    public void setTotalNumOfCustomAudiencesBeforeFiltering(
            int totalNumOfCustomAudiencesBeforeFiltering) {
        mTotalNumOfCustomAudiencesBeforeFiltering = totalNumOfCustomAudiencesBeforeFiltering;
    }

    /** Sets the number of custom audiences filtered out of bidding */
    public void setNumOfCustomAudiencesFilteredOutOfBidding(
            int numOfCustomAudiencesFilteredOutOfBidding) {
        mNumOfCustomAudiencesFilteredOutOfBidding = numOfCustomAudiencesFilteredOutOfBidding;
    }

    /** Sets the number of contextual ads before filtering */
    public void setTotalNumOfContextualAdsBeforeFiltering(
            int totalNumOfContextualAdsBeforeFiltering) {
        mTotalNumOfContextualAdsBeforeFiltering = totalNumOfContextualAdsBeforeFiltering;
    }

    /** Sets the number of contextual ads filtered */
    public void setNumOfContextualAdsFiltered(int numOfContextualAdsFiltered) {
        mNumOfContextualAdsFiltered = numOfContextualAdsFiltered;
    }

    /** Sets the number of contextual ads filtered out because they have invalid signatures */
    public void setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
            int numOfContextualAdsFilteredOutOfBiddingInvalidSignatures) {
        mNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures =
                numOfContextualAdsFilteredOutOfBiddingInvalidSignatures;
    }

    /**
     * Sets the number of contextual ads filtered of bidding because no ads remained after filtering
     */
    public void setNumOfContextualAdsFilteredOutOfBiddingNoAds(
            int numOfContextualAdsFilteredOutOfBiddingNoAds) {
        mNumOfContextualAdsFilteredOutOfBiddingNoAds = numOfContextualAdsFilteredOutOfBiddingNoAds;
    }

    /** Sets the number of ad counter keys in fcap filters */
    public void setNumOfAdCounterKeysInFcapFilters(int numOfAdCounterKeysInFcapFilters) {
        mNumOfAdCounterKeysInFcapFilters = numOfAdCounterKeysInFcapFilters;
    }

    /** Sets the number of packages in app install filters */
    public void setNumOfPackagesInAppInstallFilters(int numOfPackagesInAppInstallFilters) {
        mNumOfPackagesInAppInstallFilters = numOfPackagesInAppInstallFilters;
    }

    /** Sets the number of database operations */
    public void setNumOfDbOperations(int numOfDbOperations) {
        mNumOfDbOperations = numOfDbOperations;
    }

    /** Closes the logging operation */
    public void close() {
        if (mFilterProcessType == FILTER_PROCESS_TYPE_CONTEXTUAL_ADS) {
            sLogger.v("Logging filtering metrics for contextual ads filtering");
        } else if (mFilterProcessType == FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES) {
            sLogger.v("Logging filtering metrics for custom audience filtering");
        } else if (mFilterProcessType == FILTER_PROCESS_TYPE_UNSET) {
            sLogger.w("Filtering process type is not set! Skipping logging metrics");
            return;
        } else {
            sLogger.w(
                    "Filtering process type is invalid! It should be either %s for contextual"
                            + " ads or %s for custom audiences but it was: %s",
                    FILTER_PROCESS_TYPE_CONTEXTUAL_ADS,
                    FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES,
                    mFilterProcessType);
            return;
        }

        try {
            AdFilteringProcessAdSelectionReportedStats adFilteringStats =
                    getAdFilteringProcessAdSelectionReportedStats(mFilterProcessType);
            sLogger.v("Logging Ad Filtering Metrics: %s", adFilteringStats);
            mAdServicesLogger.logAdFilteringProcessAdSelectionReportedStats(adFilteringStats);
        } catch (Exception e) {
            sLogger.e(
                    e,
                    "Encountered error during logging ad filtering metrics for type: %s",
                    mFilterProcessType);
        }
    }

    private AdFilteringProcessAdSelectionReportedStats
            getAdFilteringProcessAdSelectionReportedStats(int filterProcessType) {
        AdFilteringProcessAdSelectionReportedStats.Builder adFilteringStats =
                AdFilteringProcessAdSelectionReportedStats.builder()
                        .setStatusCode(mStatusCode)
                        .setLatencyInMillisOfAllAdFiltering(
                                (int) (mAdFilteringEndTimestamp - mAdFilteringStartTimestamp))
                        .setLatencyInMillisOfAppInstallFiltering(
                                (int) (mAppInstallEndTimestamp - mAppInstallStartTimestamp))
                        .setLatencyInMillisOfFcapFilters(
                                (int) (mFrequencyCapEndTimestamp - mFrequencyCapStartTimestamp))
                        .setTotalNumOfAdsBeforeFiltering(mTotalNumOfAdsBeforeFiltering)
                        .setFilterProcessType(filterProcessType);

        if (filterProcessType == FILTER_PROCESS_TYPE_CONTEXTUAL_ADS) {
            adFilteringStats
                    .setNumOfContextualAdsFiltered(mNumOfContextualAdsFiltered)
                    .setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
                            mNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures)
                    .setNumOfContextualAdsFilteredOutOfBiddingNoAds(
                            mNumOfContextualAdsFilteredOutOfBiddingNoAds)
                    .setTotalNumOfContextualAdsBeforeFiltering(
                            mTotalNumOfContextualAdsBeforeFiltering);
        } else if (filterProcessType == FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES) {
            adFilteringStats
                    .setNumOfAdsFilteredOutOfBidding(mNumOfAdsFilteredOutOfBidding)
                    .setNumOfCustomAudiencesFilteredOutOfBidding(
                            mNumOfCustomAudiencesFilteredOutOfBidding)
                    .setTotalNumOfCustomAudiencesBeforeFiltering(
                            mTotalNumOfCustomAudiencesBeforeFiltering);
        }

        adFilteringStats
                .setNumOfAdCounterKeysInFcapFilters(mNumOfAdCounterKeysInFcapFilters)
                .setNumOfPackageInAppInstallFilters(mNumOfPackagesInAppInstallFilters)
                .setNumOfDbOperations(mNumOfDbOperations);

        return adFilteringStats.build();
    }

    private long getServiceElapsedTimestamp() {
        return mClock.elapsedRealtime();
    }
}
