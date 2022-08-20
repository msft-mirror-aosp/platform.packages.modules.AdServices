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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregateFilterData;
import com.android.adservices.service.measurement.noising.ImpressionNoiseParams;
import com.android.adservices.service.measurement.noising.ImpressionNoiseUtil;
import com.android.adservices.service.measurement.util.Validation;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
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

    /** Multiplier is 1, when only one destination needs to be considered. */
    public static final int SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 1;

    /**
     * Double-folds the number of states in order to allocate half to app destination and half to
     * web destination for fake reports generation.
     */
    public static final int DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 2;

    private String mId;
    private long mEventId;
    private Uri mPublisher;
    @EventSurfaceType private int mPublisherType;
    private Uri mAppDestination;
    private Uri mWebDestination;
    private Uri mAdTechDomain;
    private String mEnrollmentId;
    private Uri mRegistrant;
    private SourceType mSourceType;
    private long mPriority;
    @Status private int mStatus;
    private long mEventTime;
    private long mExpiryTime;
    private List<Long> mDedupKeys;
    @AttributionMode private int mAttributionMode;
    private long mInstallAttributionWindow;
    private long mInstallCooldownWindow;
    private @Nullable Long mDebugKey;
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
        // Making this default explicit since it anyway would occur on an uninitialised int field.
        mPublisherType = EventSurfaceType.APP;
        mAttributionMode = AttributionMode.UNASSIGNED;
        mIsInstallAttributed = false;
    }

    /** Class for storing fake report data. */
    public static class FakeReport {
        private final long mTriggerData;
        private final long mReportingTime;
        private final Uri mDestination;

        public FakeReport(long triggerData, long reportingTime, Uri destination) {
            this.mTriggerData = triggerData;
            this.mReportingTime = reportingTime;
            this.mDestination = destination;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FakeReport)) return false;
            FakeReport that = (FakeReport) o;
            return mTriggerData == that.mTriggerData
                    && mReportingTime == that.mReportingTime
                    && Objects.equals(mDestination, that.mDestination);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTriggerData, mReportingTime, mDestination);
        }

        public long getReportingTime() {
            return mReportingTime;
        }

        public long getTriggerData() {
            return mTriggerData;
        }

        public Uri getDestination() {
            return mDestination;
        }
    }

    ImpressionNoiseParams getImpressionNoiseParams() {
        int destinationMultiplier =
                (mAppDestination != null && mWebDestination != null)
                        ? DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER
                        : SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER;

        return new ImpressionNoiseParams(
                getMaxReportCountInternal(isInstallDetectionEnabled()),
                getTriggerDataCardinality(),
                getReportingWindowCountForNoising(),
                destinationMultiplier);
    }

    private ImmutableList<Long> getEarlyReportingWindows(boolean installState) {
        long[] earlyWindows;
        if (installState) {
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
    public long getReportingTimeForNoising(int windowIndex) {
        List<Long> windowList = getEarlyReportingWindows(isInstallDetectionEnabled());
        return windowIndex < windowList.size()
                ? windowList.get(windowIndex) + ONE_HOUR_IN_MILLIS :
                mExpiryTime + ONE_HOUR_IN_MILLIS;
    }

    @VisibleForTesting
    int getReportingWindowCountForNoising() {
        // Early Count + expiry
        return getEarlyReportingWindows(isInstallDetectionEnabled()).size() + 1;
    }

    /**
     * Range of trigger metadata: [0, cardinality).
     * @return Cardinality of {@link Trigger} metadata
     */
    public int getTriggerDataCardinality() {
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY
                : PrivacyParams.NAVIGATION_TRIGGER_DATA_CARDINALITY;
    }

    /**
     * Max reports count based on conversion destination type and installation state.
     *
     * @param destinationType conversion destination type
     * @return maximum number of reports allowed
     */
    public int getMaxReportCount(@NonNull @EventSurfaceType int destinationType) {
        boolean isInstallCase =
                destinationType == EventSurfaceType.APP && mIsInstallAttributed;
        return getMaxReportCountInternal(isInstallCase);
    }

    private int getMaxReportCountInternal(boolean isInstallCase) {
        if (isInstallCase) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS
                    : PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
        }
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_SOURCE_MAX_REPORTS
                : PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
    }

    /** @return Probability of selecting random state for attribution */
    public double getRandomAttributionProbability() {
        // Both destinations are set and install attribution is supported
        if (mWebDestination != null && isInstallDetectionEnabled()) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_EVENT_NOISE_PROBABILITY
                    : PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY;
        }

        // Both destinations are set but install attribution isn't supported
        if (mAppDestination != null && mWebDestination != null) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.DUAL_DESTINATION_EVENT_NOISE_PROBABILITY
                    : PrivacyParams.DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY;
        }

        // App destination is set and install attribution is supported
        if (isInstallDetectionEnabled()) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY :
                    PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY;
        }

        // One of the destinations is available without install attribution support
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_NOISE_PROBABILITY
                : PrivacyParams.NAVIGATION_NOISE_PROBABILITY;
    }

    private boolean isInstallDetectionEnabled() {
        return mInstallCooldownWindow > 0 && mAppDestination != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Source)) {
            return false;
        }
        Source source = (Source) obj;
        return Objects.equals(mId, source.mId)
                && Objects.equals(mPublisher, source.mPublisher)
                && mPublisherType == source.mPublisherType
                && Objects.equals(mAppDestination, source.mAppDestination)
                && Objects.equals(mWebDestination, source.mWebDestination)
                && Objects.equals(mAdTechDomain, source.mAdTechDomain)
                && Objects.equals(mEnrollmentId, source.mEnrollmentId)
                && mPriority == source.mPriority
                && mStatus == source.mStatus
                && mExpiryTime == source.mExpiryTime
                && mEventTime == source.mEventTime
                && mEventId == source.mEventId
                && Objects.equals(mDebugKey, source.mDebugKey)
                && mSourceType == source.mSourceType
                && Objects.equals(mDedupKeys, source.mDedupKeys)
                && Objects.equals(mRegistrant, source.mRegistrant)
                && mAttributionMode == source.mAttributionMode
                && Objects.equals(mAggregateFilterData, source.mAggregateFilterData)
                && Objects.equals(mAggregateSource, source.mAggregateSource)
                && mAggregateContributions == source.mAggregateContributions
                && Objects.equals(
                        mAggregatableAttributionSource, source.mAggregatableAttributionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mPublisher,
                mPublisherType,
                mAppDestination,
                mWebDestination,
                mAdTechDomain,
                mEnrollmentId,
                mPriority,
                mStatus,
                mExpiryTime,
                mEventTime,
                mEventId,
                mSourceType,
                mDedupKeys,
                mAggregateFilterData,
                mAggregateSource,
                mAggregateContributions,
                mAggregatableAttributionSource,
                mDebugKey);
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s expiry and
     * trigger destination type.
     *
     * @return the reporting time
     */
    public long getReportingTime(long triggerTime, @EventSurfaceType int destinationType) {
        if (triggerTime < mEventTime) {
            return -1;
        }

        // Cases where source could have both web and app destinations, there if the trigger
        // destination is an app and it was installed, then installState should be considered true.
        boolean isAppInstalled =
                destinationType == EventSurfaceType.APP && mIsInstallAttributed;
        List<Long> reportingWindows = getEarlyReportingWindows(isAppInstalled);
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
     * Assign attribution mode based on random rate and generate fake reports if needed. Should only
     * be called for a new Source.
     *
     * @return fake reports to be stored in the datastore.
     */
    public List<FakeReport> assignAttributionModeAndGenerateFakeReports() {
        Random rand = new Random();
        double value = rand.nextDouble();
        if (value > getRandomAttributionProbability()) {
            mAttributionMode = AttributionMode.TRUTHFULLY;
            return Collections.emptyList();
        }

        List<FakeReport> fakeReports;
        if (isVtcDualDestinationModeWithPostInstallEnabled()) {
            // Source is 'EVENT' type, both app and web destination are set and install exclusivity
            // window is provided. Pick one of the static reporting states randomly.
            fakeReports = generateVtcDualDestinationPostInstallFakeReports();
        } else {
            // There will at least be one (app or web) destination available
            ImpressionNoiseParams noiseParams = getImpressionNoiseParams();
            fakeReports =
                    ImpressionNoiseUtil.selectRandomStateAndGenerateReportConfigs(noiseParams, rand)
                            .stream()
                            .map(
                                    reportConfig ->
                                            new FakeReport(
                                                    reportConfig[0],
                                                    getReportingTimeForNoising(reportConfig[1]),
                                                    resolveFakeReportDestination(reportConfig[2])))
                            .collect(Collectors.toList());
        }

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
     * AdTech enrollment ID
     */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** Uri which registered the {@link Source}. */
    public Uri getPublisher() {
        return mPublisher;
    }

    /** The publisher type (e.g., app or web) {@link Source}. */
    @EventSurfaceType
    public int getPublisherType() {
        return mPublisherType;
    }

    /** Uri for the {@link Trigger}'s app destination. */
    @Nullable
    public Uri getAppDestination() {
        return mAppDestination;
    }

    /** Uri for the {@link Trigger}'s web destination. */
    @Nullable
    public Uri getWebDestination() {
        return mWebDestination;
    }

    /**
     * Type of {@link Source}. Values: Event, Navigation.
     */
    public SourceType getSourceType() {
        return mSourceType;
    }

    /** Time when {@link Source} will expiry. */
    public long getExpiryTime() {
        return mExpiryTime;
    }

    /** Debug key of {@link Source}. */
    public @Nullable Long getDebugKey() {
        return mDebugKey;
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

    /** Current status of the {@link Source}. */
    @Status
    public int getStatus() {
        return mStatus;
    }

    /**
     * Registrant of this source, primarily an App.
     */
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /** Selected mode for attribution. Values: Truthfully, Never, Falsely. */
    @AttributionMode
    public int getAttributionMode() {
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

    /** Set app install attribution to the {@link Source}. */
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
        return Optional.of(
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregateSourceMap)
                        .setAggregateFilterData(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(
                                                new JSONObject(this.mAggregateFilterData))
                                        .build())
                        .build());
    }

    private List<FakeReport> generateVtcDualDestinationPostInstallFakeReports() {
        int[][][] fakeReportsConfig =
                ImpressionNoiseUtil.DUAL_DESTINATION_POST_INSTALL_FAKE_REPORT_CONFIG;
        int randomIndex = new Random().nextInt(fakeReportsConfig.length);
        int[][] reportsConfig = fakeReportsConfig[randomIndex];
        return Arrays.stream(reportsConfig)
                .map(
                        reportConfig ->
                                new FakeReport(
                                        reportConfig[0],
                                        getReportingTimeForNoising(reportConfig[1]),
                                        resolveFakeReportDestination(reportConfig[2])))
                .collect(Collectors.toList());
    }

    private boolean isVtcDualDestinationModeWithPostInstallEnabled() {
        return mSourceType == SourceType.EVENT
                && mWebDestination != null
                && isInstallDetectionEnabled();
    }

    /**
     * Either both app and web destinations can be available or one of them will be available. When
     * both destinations are available, we double the number of states at noise generation to be
     * able to randomly choose one of them for fake report creation. We don't add the multiplier
     * when only one of them is available. In that case, choose the one that's non-null.
     *
     * @param destinationIdentifier destination identifier, can be 0 (app) or 1 (web)
     * @return app or web destination {@link Uri}
     */
    private Uri resolveFakeReportDestination(int destinationIdentifier) {
        if (mAppDestination != null && mWebDestination != null) {
            // It could be a direct destinationIdentifier == 0 check, but
            return destinationIdentifier % DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER == 0
                    ? mAppDestination
                    : mWebDestination;
        }

        return mAppDestination != null ? mAppDestination : mWebDestination;
    }

    /**
     * Builder for {@link Source}.
     */
    public static final class Builder {
        private final Source mBuilding;
        public Builder() {
            mBuilding = new Source();
        }

        /** See {@link Source#getId()}. */
        @NonNull
        public Builder setId(@NonNull String id) {
            Validation.validateNonNull(id);
            mBuilding.mId = id;
            return this;
        }

        /** See {@link Source#getEventId()}. */
        @NonNull
        public Builder setEventId(long eventId) {
            mBuilding.mEventId = eventId;
            return this;
        }

        /** See {@link Source#getPublisher()}. */
        @NonNull
        public Builder setPublisher(@NonNull Uri publisher) {
            Validation.validateUri(publisher);
            mBuilding.mPublisher = publisher;
            return this;
        }

        /** See {@link Source#getPublisherType()}. */
        @NonNull
        public Builder setPublisherType(@EventSurfaceType int publisherType) {
            mBuilding.mPublisherType = publisherType;
            return this;
        }

        /** See {@link Source#getAppDestination()}. */
        public Builder setAppDestination(Uri appDestination) {
            Optional.ofNullable(appDestination).ifPresent(Validation::validateUri);
            mBuilding.mAppDestination = appDestination;
            return this;
        }

        /** See {@link Source#getWebDestination()}. */
        @NonNull
        public Builder setWebDestination(@Nullable Uri webDestination) {
            Optional.ofNullable(webDestination).ifPresent(Validation::validateUri);
            mBuilding.mWebDestination = webDestination;
            return this;
        }

        /** See {@link Source#getAdTechDomain()} ()}. */
        @NonNull
        public Builder setAdTechDomain(@NonNull Uri adTechDomain) {
            Validation.validateUri(adTechDomain);
            mBuilding.mAdTechDomain = adTechDomain;
            return this;
        }

        /** See {@link Source#getEnrollmentId()} ()}. */
        @NonNull
        public Builder setEnrollmentId(@NonNull String enrollmentId) {
            mBuilding.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link Source#getEventId()}. */
        @NonNull
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

        /** See {@link Source#getPriority()}. */
        @NonNull
        public Builder setPriority(long priority) {
            mBuilding.mPriority = priority;
            return this;
        }

        /** See {@link Source#getDebugKey()} ()}. */
        public Builder setDebugKey(@Nullable Long debugKey) {
            mBuilding.mDebugKey = debugKey;
            return this;
        }

        /** See {@link Source#getSourceType()}. */
        @NonNull
        public Builder setSourceType(@NonNull SourceType sourceType) {
            Validation.validateNonNull(sourceType);
            mBuilding.mSourceType = sourceType;
            return this;
        }

        /** See {@link Source#getDedupKeys()}. */
        @NonNull
        public Builder setDedupKeys(@Nullable List<Long> dedupKeys) {
            mBuilding.mDedupKeys = dedupKeys;
            return this;
        }

        /** See {@link Source#getStatus()}. */
        @NonNull
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /** See {@link Source#getRegistrant()} */
        @NonNull
        public Builder setRegistrant(@NonNull Uri registrant) {
            Validation.validateUri(registrant);
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /** See {@link Source#getAttributionMode()} */
        @NonNull
        public Builder setAttributionMode(@AttributionMode int attributionMode) {
            mBuilding.mAttributionMode = attributionMode;
            return this;
        }

        /** See {@link Source#getInstallAttributionWindow()} */
        @NonNull
        public Builder setInstallAttributionWindow(long installAttributionWindow) {
            mBuilding.mInstallAttributionWindow = installAttributionWindow;
            return this;
        }

        /** See {@link Source#getInstallCooldownWindow()} */
        @NonNull
        public Builder setInstallCooldownWindow(long installCooldownWindow) {
            mBuilding.mInstallCooldownWindow = installCooldownWindow;
            return this;
        }

        /** See {@link Source#isInstallAttributed()} */
        @NonNull
        public Builder setInstallAttributed(boolean installAttributed) {
            mBuilding.mIsInstallAttributed = installAttributed;
            return this;
        }

        /** See {@link Source#getAggregateFilterData()}. */
        public Builder setAggregateFilterData(@Nullable String aggregateFilterData) {
            mBuilding.mAggregateFilterData = aggregateFilterData;
            return this;
        }

        /** See {@link Source#getAggregateSource()} */
        public Builder setAggregateSource(@Nullable String aggregateSource) {
            mBuilding.mAggregateSource = aggregateSource;
            return this;
        }

        /** See {@link Source#getAggregateContributions()} */
        @NonNull
        public Builder setAggregateContributions(int aggregateContributions) {
            mBuilding.mAggregateContributions = aggregateContributions;
            return this;
        }

        /** See {@link Source#getAggregatableAttributionSource()} */
        @NonNull
        public Builder setAggregatableAttributionSource(
                @Nullable AggregatableAttributionSource aggregatableAttributionSource) {
            mBuilding.mAggregatableAttributionSource = aggregatableAttributionSource;
            return this;
        }

        /**
         * Build the {@link Source}.
         */
        public Source build() {
            Validation.validateNonNull(
                    mBuilding.mPublisher,
                    mBuilding.mAdTechDomain,
                    // TODO (b/238924528): uncomment when enforcing enrollment.
                    //mBuilding.mEnrollmentId,
                    mBuilding.mRegistrant,
                    mBuilding.mSourceType);

            if (mBuilding.mAppDestination == null && mBuilding.mWebDestination == null) {
                throw new IllegalArgumentException("At least one destination is required");
            }

            return mBuilding;
        }
    }
}
