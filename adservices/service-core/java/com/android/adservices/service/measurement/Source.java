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

package com.android.adservices.service.measurement;

import android.annotation.IntDef;
import android.net.Uri;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregateFilterData;
import com.android.adservices.service.measurement.noising.ImpressionNoiseParams;
import com.android.adservices.service.measurement.noising.ImpressionNoiseUtil;
import com.android.adservices.service.measurement.validation.Validation;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * POJO for Source.
 */
public class Source {

    private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);

    private String mId;
    private long mEventId;
    private Uri mPublisher;
    private Uri mAttributionDestination;
    private Uri mAdTechDomain;
    private Uri mRegistrant;
    private SourceType mSourceType;
    private long mPriority;
    private @Status int mStatus;
    private long mEventTime;
    private long mExpiryTime;
    private List<Long> mDedupKeys;
    private @AttributionMode int mAttributionMode;
    private long mInstallAttributionWindow;
    private long mInstallCooldownWindow;
    private boolean mIsInstallAttributed;
    private String mAggregateFilterData;
    private String mAggregateSource;
    private int mAggregateContributions;
    private AggregatableAttributionSource mAggregatableAttributionSource;

    @IntDef(value = {
            Status.ACTIVE,
            Status.IGNORED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int ACTIVE = 0;
        int IGNORED = 1;
    }

    @IntDef(value = {
            AttributionMode.UNASSIGNED,
            AttributionMode.TRUTHFULLY,
            AttributionMode.NEVER,
            AttributionMode.FALSELY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AttributionMode {
        int UNASSIGNED = 0;
        int TRUTHFULLY = 1;
        int NEVER = 2;
        int FALSELY = 3;
    }

    public enum SourceType {
        EVENT("event"),
        NAVIGATION("navigation");

        private final String mValue;

        SourceType(String value) {
            this.mValue = value;
        }

        public String getValue() {
            return mValue;
        }
    }

    private Source() {
        mDedupKeys = new ArrayList<>();
        mStatus = Status.ACTIVE;
        mSourceType = SourceType.EVENT;
        mAttributionMode = AttributionMode.UNASSIGNED;
        mIsInstallAttributed = false;
    }

    /**
     * Class for storing fake report data.
     */
    public static class FakeReport {
        private final long mTriggerData;
        private final long mReportingTime;
        private FakeReport(long triggerData, long reportingTime) {
            this.mTriggerData = triggerData;
            this.mReportingTime = reportingTime;
        }

        public long getReportingTime() {
            return mReportingTime;
        }

        public long getTriggerData() {
            return mTriggerData;
        }
    }

    ImpressionNoiseParams getImpressionNoiseParams() {
        return new ImpressionNoiseParams(
                getMaxReportCountInternal(/* considerAttrState= */ false),
                getTriggerDataCardinality(),
                getReportingWindowCountForNoising());
    }

    private ImmutableList<Long> getEarlyReportingWindows(boolean considerAttrState) {
        long[] earlyWindows;
        if (useInstallAttrParams(considerAttrState)) {
            earlyWindows = mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                    : PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        } else {
            earlyWindows = mSourceType == SourceType.EVENT
                    ? PrivacyParams.EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                    : PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        }

        List<Long> windowList = new ArrayList<>();

        for (long windowDelta : earlyWindows) {
            long window = mEventTime + windowDelta;
            if (mExpiryTime <= window) {
                continue;
            }
            windowList.add(window);
        }
        return ImmutableList.copyOf(windowList);
    }

    /**
     * Return reporting time by index for noising based on the index
     *
     * @param windowIndex index of the reporting window for which
     * @return reporting time in milliseconds
     */
    @VisibleForTesting
    public long getReportingTimeForNoising(int windowIndex) {
        List<Long> windowList = getEarlyReportingWindows(/* considerAttrState= */ false);
        return windowIndex < windowList.size()
                ? windowList.get(windowIndex) + ONE_HOUR_IN_MILLIS :
                mExpiryTime + ONE_HOUR_IN_MILLIS;
    }

    @VisibleForTesting
    int getReportingWindowCountForNoising() {
        // Early Count + expiry
        return getEarlyReportingWindows(/* considerAttrState= */ false).size() + 1;
    }

    /**
     * Range of trigger metadata: [0, cardinality).
     * @return Cardinality of {@link Trigger} metadata
     */
    public int getTriggerDataCardinality() {
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY :
                PrivacyParams.NAVIGATION_TRIGGER_DATA_CARDINALITY;
    }

    /**
     * @return Maximum number of reports allowed
     */
    public int getMaxReportCount() {
        return getMaxReportCountInternal(/* considerAttrState= */ true);
    }

    private int getMaxReportCountInternal(boolean considerAttrState) {
        if (useInstallAttrParams(considerAttrState)) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS
                    : PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
        }
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_SOURCE_MAX_REPORTS
                : PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
    }

    /**
     * @return Probability of selecting random state for attribution
     */
    public double getRandomAttributionProbability() {
        if (isInstallDetectionEnabled()) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY :
                    PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY;
        }
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_NOISE_PROBABILITY :
                PrivacyParams.NAVIGATION_NOISE_PROBABILITY;
    }

    private boolean useInstallAttrParams(boolean considerAttrState) {
        if (considerAttrState) {
            return mIsInstallAttributed;
        }
        return isInstallDetectionEnabled();
    }

    private boolean isInstallDetectionEnabled() {
        return mInstallCooldownWindow > 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Source)) {
            return false;
        }
        Source source = (Source) obj;
        return Objects.equals(mId, source.mId)
                && Objects.equals(mPublisher, source.mPublisher)
                && Objects.equals(mAttributionDestination, source.mAttributionDestination)
                && Objects.equals(mAdTechDomain, source.mAdTechDomain)
                && mPriority == source.mPriority
                && mStatus == source.mStatus
                && mExpiryTime == source.mExpiryTime
                && mEventTime == source.mEventTime
                && mEventId == source.mEventId
                && mSourceType == source.mSourceType
                && Objects.equals(mDedupKeys, source.mDedupKeys)
                && Objects.equals(mRegistrant, source.mRegistrant)
                && mAttributionMode == source.mAttributionMode
                && Objects.equals(mAggregateFilterData, source.mAggregateFilterData)
                && Objects.equals(mAggregateSource, source.mAggregateSource)
                && mAggregateContributions == source.mAggregateContributions
                && Objects.equals(mAggregatableAttributionSource,
                source.mAggregatableAttributionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mPublisher, mAttributionDestination, mAdTechDomain, mPriority,
                mStatus, mExpiryTime, mEventTime, mEventId, mSourceType, mDedupKeys,
                mAggregateFilterData, mAggregateSource, mAggregateContributions,
                mAggregatableAttributionSource);
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} Time and
     * {@link Source}'s expiry.
     *
     * @return the report time
     */
    public long getReportingTime(long triggerTime) {
        if (triggerTime < mEventTime) {
            return -1;
        }
        List<Long> reportingWindows = getEarlyReportingWindows(/* considerAttrState= */ true);
        for (Long window: reportingWindows) {
            if (triggerTime < window) {
                return window + ONE_HOUR_IN_MILLIS;
            }
        }
        return mExpiryTime + ONE_HOUR_IN_MILLIS;
    }

    @VisibleForTesting
    void setAttributionMode(@AttributionMode int attributionMode) {
        mAttributionMode = attributionMode;
    }

    /**
     * Assign attribution mode based on random rate and generate fake reports if needed.
     * Should only be called for a new Source.
     * @return fake reports to be stored in the datastore.
     */
    public List<FakeReport> assignAttributionModeAndGenerateFakeReport() {
        Random rand = new Random();
        double value = rand.nextDouble();
        if (value > getRandomAttributionProbability()) {
            mAttributionMode = AttributionMode.TRUTHFULLY;
            return Collections.emptyList();
        }
        ImpressionNoiseParams noiseParams = getImpressionNoiseParams();
        List<FakeReport> fakeReports = ImpressionNoiseUtil
                .selectRandomStateAndGenerateReportConfigs(noiseParams, rand)
                .stream().map(reportConfig -> new FakeReport(reportConfig[0],
                        getReportingTimeForNoising(reportConfig[1])))
                .collect(Collectors.toList());
        mAttributionMode = fakeReports.isEmpty() ? AttributionMode.NEVER : AttributionMode.FALSELY;
        return fakeReports;
    }

    /**
     * Unique identifier for the {@link Source}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Identifier provided by the registrant.
     */
    public long getEventId() {
        return mEventId;
    }

    /**
     * Priority of the {@link Source}.
     */
    public long getPriority() {
        return mPriority;
    }

    /**
     * AdTech reporting destination domain for generated reports.
     */
    public Uri getAdTechDomain() {
        return mAdTechDomain;
    }

    /**
     * Uri which registered the {@link Source}.
     */
    public Uri getPublisher() {
        return mPublisher;
    }

    /**
     * Uri for the {@link Trigger}'s.
     */
    public Uri getAttributionDestination() {
        return mAttributionDestination;
    }

    /**
     * Type of {@link Source}. Values: Event, Navigation.
     */
    public SourceType getSourceType() {
        return mSourceType;
    }

    /**
     * Time when {@link Source} will expiry.
     */
    public long getExpiryTime() {
        return mExpiryTime;
    }

    /**
     * Time the event occurred.
     */
    public long getEventTime() {
        return mEventTime;
    }

    /**
     * List of dedup keys for the attributed {@link Trigger}.
     */
    public List<Long> getDedupKeys() {
        return mDedupKeys;
    }

    /**
     * Current status of the {@link Source}.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Registrant of this source, primarily an App.
     */
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /**
     * Selected mode for attribution. Values: Truthfully, Never, Falsely.
     */
    public @AttributionMode int getAttributionMode() {
        return mAttributionMode;
    }

    /**
     * Attribution window for install events.
     */
    public long getInstallAttributionWindow() {
        return mInstallAttributionWindow;
    }

    /**
     * Cooldown for attributing post-install {@link Trigger} events.
     */
    public long getInstallCooldownWindow() {
        return mInstallCooldownWindow;
    }

    /**
     * Is an App-install attributed to the {@link Source}.
     */
    public boolean isInstallAttributed() {
        return mIsInstallAttributed;
    }

    /**
     * Returns aggregate filter data string used for aggregation. aggregate filter data json is a
     * JSONObject in Attribution-Reporting-Register-Source header.
     * Example:
     * Attribution-Reporting-Register-Source: {
     *   // some other fields.
     *   "filter_data" : {
     *    "conversion_subdomain": ["electronics.megastore"],
     *    "product": ["1234", "2345"],
     *    "ctid": ["id"],
     *    ......
     * }
     * }
     */
    public String getAggregateFilterData() {
        return mAggregateFilterData;
    }

    /**
     * Returns aggregate source string used for aggregation. aggregate source json is a JSONArray.
     * Example:
     * [{
     *   // Generates a "0x159" key piece (low order bits of the key) named
     *   // "campaignCounts"
     *   "id": "campaignCounts",
     *   "key_piece": "0x159", // User saw ad from campaign 345 (out of 511)
     * },
     * {
     *   // Generates a "0x5" key piece (low order bits of the key) named "geoValue"
     *   "id": "geoValue",
     *   // Source-side geo region = 5 (US), out of a possible ~100 regions.
     *   "key_piece": "0x5",
     * }]
     */
    public String getAggregateSource() {
        return mAggregateSource;
    }

    /**
     * Returns the current sum of values the source contributed to aggregatable reports.
     */
    public int getAggregateContributions() {
        return mAggregateContributions;
    }

    /**
     * Returns the AggregatableAttributionSource object, which is constructed using the aggregate
     * source string and aggregate filter data string in Source.
     */
    public AggregatableAttributionSource getAggregatableAttributionSource() {
        return mAggregatableAttributionSource;
    }

    /**
     * Set app install attribution to the {@link Source}.
     */
    public void setInstallAttributed(boolean isInstallAttributed) {
        mIsInstallAttributed = isInstallAttributed;
    }

    /**
     * Set the status.
     */
    public void setStatus(@Status int status) {
        mStatus = status;
    }

    /**
     * Set the aggregate contributions value.
     */
    public void setAggregateContributions(int aggregateContributions) {
        mAggregateContributions = aggregateContributions;
    }

    /**
     * Generates AggregatableAttributionSource from aggregate source string and aggregate filter
     * data string in Source.
     */
    public Optional<AggregatableAttributionSource> parseAggregateSource()
            throws JSONException, NumberFormatException {
        if (this.mAggregateSource == null) {
            return Optional.empty();
        }
        JSONArray jsonArray = new JSONArray(this.mAggregateSource);
        Map<String, BigInteger> aggregateSourceMap = new HashMap<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String id = jsonObject.getString("id");
            String hexString = jsonObject.getString("key_piece");
            if (hexString.startsWith("0x")) {
                hexString = hexString.substring(2);
            }
            BigInteger bigInteger = new BigInteger(hexString, 16);
            aggregateSourceMap.put(id, bigInteger);
        }
        return Optional.of(new AggregatableAttributionSource.Builder()
                .setAggregatableSource(aggregateSourceMap)
                .setAggregateFilterData(
                        new AggregateFilterData.Builder().buildAggregateFilterData(
                                new JSONObject(this.mAggregateFilterData)).build())
                .build());
    }

    /**
     * Builder for {@link Source}.
     */
    public static final class Builder {
        private final Source mBuilding;
        public Builder() {
            mBuilding = new Source();
        }

        /**
         * See {@link Source#getId()}.
         */
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /**
         * See {@link Source#getEventId()}.
         */
        public Builder setEventId(long eventId) {
            mBuilding.mEventId = eventId;
            return this;
        }

        /**
         * See {@link Source#getPublisher()}.
         */
        public Builder setPublisher(Uri publisher) {
            Validation.validateUri(publisher);
            mBuilding.mPublisher = publisher;
            return this;
        }

        /**
         * See {@link Source#getAttributionDestination()}.
         */

        public Builder setAttributionDestination(Uri attributionDestination) {
            Validation.validateUri(attributionDestination);
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link Source#getAdTechDomain()} ()}.
         */
        public Builder setAdTechDomain(Uri adTechDomain) {
            Validation.validateUri(adTechDomain);
            mBuilding.mAdTechDomain = adTechDomain;
            return this;
        }

        /**
         * See {@link Source#getEventId()}.
         */
        public Builder setEventTime(long eventTime) {
            mBuilding.mEventTime = eventTime;
            return this;
        }

        /**
         * See {@link Source#getExpiryTime()}.
         */
        public Builder setExpiryTime(long expiryTime) {
            mBuilding.mExpiryTime = expiryTime;
            return this;
        }

        /**
         * See {@link Source#getPriority()}.
         */
        public Builder setPriority(long priority) {
            mBuilding.mPriority = priority;
            return this;
        }

        /**
         * See {@link Source#getSourceType()}.
         */
        public Builder setSourceType(SourceType sourceType) {
            mBuilding.mSourceType = sourceType;
            return this;
        }

        /**
         * See {@link Source#getDedupKeys()}.
         */
        public Builder setDedupKeys(List<Long> dedupKeys) {
            mBuilding.mDedupKeys = dedupKeys;
            return this;
        }

        /**
         * See {@link Source#getStatus()}.
         */
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /**
         * See {@link Source#getRegistrant()}
         */
        public Builder setRegistrant(Uri registrant) {
            Validation.validateUri(registrant);
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /**
         * See {@link Source#getAttributionMode()}
         */
        public Builder setAttributionMode(@AttributionMode int attributionMode) {
            mBuilding.mAttributionMode = attributionMode;
            return this;
        }

        /**
         * See {@link Source#getInstallAttributionWindow()}
         */
        public Builder setInstallAttributionWindow(long installAttributionWindow) {
            mBuilding.mInstallAttributionWindow = installAttributionWindow;
            return this;
        }

        /**
         * See {@link Source#getInstallCooldownWindow()}
         */
        public Builder setInstallCooldownWindow(long installCooldownWindow) {
            mBuilding.mInstallCooldownWindow = installCooldownWindow;
            return this;
        }

        /**
         * See {@link Source#isInstallAttributed()}
         */
        public Builder setInstallAttributed(boolean installAttributed) {
            mBuilding.mIsInstallAttributed = installAttributed;
            return this;
        }

        /**
         * See {@link Source#getAggregateFilterData()}.
         */
        public Builder setAggregateFilterData(String aggregateFilterData) {
            mBuilding.mAggregateFilterData = aggregateFilterData;
            return this;
        }

        /**
         * See {@link Source#getAggregateSource()}
         */
        public Builder setAggregateSource(String aggregateSource) {
            mBuilding.mAggregateSource = aggregateSource;
            return this;
        }

        /**
         * See {@link Source#getAggregateContributions()}
         */
        public Builder setAggregateContributions(int aggregateContributions) {
            mBuilding.mAggregateContributions = aggregateContributions;
            return this;
        }

        /**
         * See {@link Source#getAggregatableAttributionSource()}
         */
        public Builder setAggregatableAttributionSource(
                AggregatableAttributionSource aggregatableAttributionSource) {
            mBuilding.mAggregatableAttributionSource = aggregatableAttributionSource;
            return this;
        }

        /**
         * Build the {@link Source}.
         */
        public Source build() {
            Validation.validateNonNull(
                    mBuilding.mPublisher,
                    mBuilding.mAttributionDestination,
                    mBuilding.mAdTechDomain,
                    mBuilding.mRegistrant,
                    mBuilding.mSourceType);

            return mBuilding;
        }
    }
}
