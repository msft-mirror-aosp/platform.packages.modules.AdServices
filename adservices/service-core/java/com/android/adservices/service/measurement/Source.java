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
import com.android.adservices.service.measurement.noising.ImpressionNoiseParams;
import com.android.adservices.service.measurement.noising.ImpressionNoiseUtil;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.measurement.util.Validation;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
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
    private UnsignedLong mEventId;
    private Uri mPublisher;
    @EventSurfaceType private int mPublisherType;
    private List<Uri> mAppDestinations;
    private List<Uri> mWebDestinations;
    private String mEnrollmentId;
    private Uri mRegistrant;
    private SourceType mSourceType;
    private long mPriority;
    @Status private int mStatus;
    private long mEventTime;
    private long mExpiryTime;
    private long mEventReportWindow;
    private long mAggregatableReportWindow;
    private List<UnsignedLong> mAggregateReportDedupKeys;
    private List<UnsignedLong> mEventReportDedupKeys;
    @AttributionMode private int mAttributionMode;
    private long mInstallAttributionWindow;
    private long mInstallCooldownWindow;
    @Nullable private UnsignedLong mDebugKey;
    private boolean mIsInstallAttributed;
    private boolean mIsDebugReporting;
    private String mFilterDataString;
    private FilterMap mFilterData;
    private String mAggregateSource;
    private int mAggregateContributions;
    private Optional<AggregatableAttributionSource> mAggregatableAttributionSource;
    private boolean mAdIdPermission;
    private boolean mArDebugPermission;
    @Nullable private String mRegistrationId;
    @Nullable private String mSharedAggregationKeys;
    @Nullable private Long mInstallTime;
    @Nullable private String mParentId;
    @Nullable private String mDebugJoinKey;

    @IntDef(value = {Status.ACTIVE, Status.IGNORED, Status.MARKED_TO_DELETE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int ACTIVE = 0;
        int IGNORED = 1;
        int MARKED_TO_DELETE = 2;
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
            mValue = value;
        }

        public String getValue() {
            return mValue;
        }
    }

    private Source() {
        mEventReportDedupKeys = new ArrayList<>();
        mAggregateReportDedupKeys = new ArrayList<>();
        mStatus = Status.ACTIVE;
        mSourceType = SourceType.EVENT;
        // Making this default explicit since it anyway would occur on an uninitialised int field.
        mPublisherType = EventSurfaceType.APP;
        mAttributionMode = AttributionMode.UNASSIGNED;
        mIsInstallAttributed = false;
        mIsDebugReporting = false;
    }

    /** Class for storing fake report data. */
    public static class FakeReport {
        private final UnsignedLong mTriggerData;
        private final long mReportingTime;
        private final List<Uri> mDestinations;

        public FakeReport(UnsignedLong triggerData, long reportingTime, List<Uri> destinations) {
            mTriggerData = triggerData;
            mReportingTime = reportingTime;
            mDestinations = destinations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FakeReport)) return false;
            FakeReport that = (FakeReport) o;
            return Objects.equals(mTriggerData, that.mTriggerData)
                    && mReportingTime == that.mReportingTime
                    && Objects.equals(mDestinations, that.mDestinations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTriggerData, mReportingTime, mDestinations);
        }

        public long getReportingTime() {
            return mReportingTime;
        }

        public UnsignedLong getTriggerData() {
            return mTriggerData;
        }

        public List<Uri> getDestinations() {
            return mDestinations;
        }
    }

    ImpressionNoiseParams getImpressionNoiseParams() {
        int destinationMultiplier =
                hasAppDestinations() && hasWebDestinations()
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
            if (mEventReportWindow <= window) {
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
                mEventReportWindow + ONE_HOUR_IN_MILLIS;
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
                : PrivacyParams.getNavigationTriggerDataCardinality();
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
        if (hasWebDestinations() && isInstallDetectionEnabled()) {
            return mSourceType == SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_EVENT_NOISE_PROBABILITY
                    : PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY;
        }

        // Both destinations are set but install attribution isn't supported
        if (hasAppDestinations() && hasWebDestinations()) {
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
        return mInstallCooldownWindow > 0 && hasAppDestinations();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Source)) {
            return false;
        }
        Source source = (Source) obj;
        return Objects.equals(mPublisher, source.mPublisher)
                && mPublisherType == source.mPublisherType
                && areEqualNullableDestinations(mAppDestinations, source.mAppDestinations)
                && areEqualNullableDestinations(mWebDestinations, source.mWebDestinations)
                && Objects.equals(mEnrollmentId, source.mEnrollmentId)
                && mPriority == source.mPriority
                && mStatus == source.mStatus
                && mExpiryTime == source.mExpiryTime
                && mEventReportWindow == source.mEventReportWindow
                && mAggregatableReportWindow == source.mAggregatableReportWindow
                && mEventTime == source.mEventTime
                && mAdIdPermission == source.mAdIdPermission
                && mArDebugPermission == source.mArDebugPermission
                && Objects.equals(mEventId, source.mEventId)
                && Objects.equals(mDebugKey, source.mDebugKey)
                && mSourceType == source.mSourceType
                && Objects.equals(mEventReportDedupKeys, source.mEventReportDedupKeys)
                && Objects.equals(mAggregateReportDedupKeys, source.mAggregateReportDedupKeys)
                && Objects.equals(mRegistrant, source.mRegistrant)
                && mAttributionMode == source.mAttributionMode
                && mIsDebugReporting == source.mIsDebugReporting
                && Objects.equals(mFilterDataString, source.mFilterDataString)
                && Objects.equals(mAggregateSource, source.mAggregateSource)
                && mAggregateContributions == source.mAggregateContributions
                && Objects.equals(
                        mAggregatableAttributionSource, source.mAggregatableAttributionSource)
                && Objects.equals(mRegistrationId, source.mRegistrationId)
                && Objects.equals(mSharedAggregationKeys, source.mSharedAggregationKeys)
                && Objects.equals(mParentId, source.mParentId)
                && Objects.equals(mInstallTime, source.mInstallTime)
                && Objects.equals(mDebugJoinKey, source.mDebugJoinKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mPublisher,
                mPublisherType,
                mAppDestinations,
                mWebDestinations,
                mEnrollmentId,
                mPriority,
                mStatus,
                mExpiryTime,
                mEventReportWindow,
                mAggregatableReportWindow,
                mEventTime,
                mEventId,
                mSourceType,
                mEventReportDedupKeys,
                mAggregateReportDedupKeys,
                mFilterDataString,
                mAggregateSource,
                mAggregateContributions,
                mAggregatableAttributionSource,
                mDebugKey,
                mAdIdPermission,
                mArDebugPermission,
                mRegistrationId,
                mSharedAggregationKeys,
                mInstallTime,
                mDebugJoinKey);
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
            if (triggerTime <= window) {
                return window + ONE_HOUR_IN_MILLIS;
            }
        }
        return mEventReportWindow + ONE_HOUR_IN_MILLIS;
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
                                                    new UnsignedLong(Long.valueOf(reportConfig[0])),
                                                    getReportingTimeForNoising(reportConfig[1]),
                                                    resolveFakeReportDestinations(reportConfig[2])))
                            .collect(Collectors.toList());
        }

        mAttributionMode = fakeReports.isEmpty() ? AttributionMode.NEVER : AttributionMode.FALSELY;
        return fakeReports;
    }

    /**
     * Retrieve the attribution destinations corresponding to their destination type.
     *
     * @return a list of Uris.
     */
    public List<Uri> getAttributionDestinations(@EventSurfaceType int destinationType) {
        return destinationType == EventSurfaceType.APP ? mAppDestinations : mWebDestinations;
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
    public UnsignedLong getEventId() {
        return mEventId;
    }

    /**
     * Priority of the {@link Source}.
     */
    public long getPriority() {
        return mPriority;
    }

    /**
     * Ad Tech enrollment ID
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

    /** Uris for the {@link Trigger}'s app destinations. */
    @Nullable
    public List<Uri> getAppDestinations() {
        return mAppDestinations;
    }

    /** Uris for the {@link Trigger}'s web destinations. */
    @Nullable
    public List<Uri> getWebDestinations() {
        return mWebDestinations;
    }

    /**
     * Type of {@link Source}. Values: Event, Navigation.
     */
    public SourceType getSourceType() {
        return mSourceType;
    }

    /** Time when {@link Source} will expire. */
    public long getExpiryTime() {
        return mExpiryTime;
    }

    /** Time when {@link Source} event report window will expire. */
    public long getEventReportWindow() {
        return mEventReportWindow;
    }

    /** Time when {@link Source} aggregate report window will expire. */
    public long getAggregatableReportWindow() {
        return mAggregatableReportWindow;
    }

    /** Debug key of {@link Source}. */
    public @Nullable UnsignedLong getDebugKey() {
        return mDebugKey;
    }

    /**
     * Time the event occurred.
     */
    public long getEventTime() {
        return mEventTime;
    }

    /** Is Ad ID Permission Enabled. */
    public boolean hasAdIdPermission() {
        return mAdIdPermission;
    }

    /** Is Ar Debug Permission Enabled. */
    public boolean hasArDebugPermission() {
        return mArDebugPermission;
    }

    /** List of dedup keys for the attributed {@link Trigger}. */
    public List<UnsignedLong> getEventReportDedupKeys() {
        return mEventReportDedupKeys;
    }

    /** List of dedup keys used for generating Aggregate Reports. */
    public List<UnsignedLong> getAggregateReportDedupKeys() {
        return mAggregateReportDedupKeys;
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

    /** Is Ad Tech Opt-in to Debug Reporting {@link Source}. */
    public boolean isDebugReporting() {
        return mIsDebugReporting;
    }

    /**
     * Returns aggregate filter data string used for aggregation. aggregate filter data json is a
     * JSONObject in Attribution-Reporting-Register-Source header. Example:
     * Attribution-Reporting-Register-Source: { // some other fields. "filter_data" : {
     * "conversion_subdomain": ["electronics.megastore"], "product": ["1234", "2345"], "ctid":
     * ["id"], ...... } }
     */
    public String getFilterDataString() {
        return mFilterDataString;
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
    public Optional<AggregatableAttributionSource> getAggregatableAttributionSource()
            throws JSONException {
        if (mAggregatableAttributionSource == null) {
            if (mAggregateSource == null) {
                mAggregatableAttributionSource = Optional.empty();
                return mAggregatableAttributionSource;
            }
            JSONObject jsonObject = new JSONObject(mAggregateSource);
            TreeMap<String, BigInteger> aggregateSourceMap = new TreeMap<>();
            for (String key : jsonObject.keySet()) {
                // Remove "0x" prefix.
                String hexString = jsonObject.getString(key).substring(2);
                BigInteger bigInteger = new BigInteger(hexString, 16);
                aggregateSourceMap.put(key, bigInteger);
            }
            AggregatableAttributionSource aggregatableAttributionSource =
                    new AggregatableAttributionSource.Builder()
                            .setAggregatableSource(aggregateSourceMap)
                            .setFilterMap(getFilterData())
                            .build();
            mAggregatableAttributionSource = Optional.of(aggregatableAttributionSource);
        }

        return mAggregatableAttributionSource;
    }

    /** Returns the registration id. */
    @Nullable
    public String getRegistrationId() {
        return mRegistrationId;
    }

    /**
     * Returns the shared aggregation keys of the source as a unique list of strings. Example:
     * [“campaignCounts”]
     */
    @Nullable
    public String getSharedAggregationKeys() {
        return mSharedAggregationKeys;
    }

    /** Returns the install time of the source which is the same value as event time. */
    @Nullable
    public Long getInstallTime() {
        return mInstallTime;
    }

    /**
     * Returns join key that should be matched with trigger's join key at the time of generating
     * reports.
     */
    @Nullable
    public String getDebugJoinKey() {
        return mDebugJoinKey;
    }

    /** See {@link Source#getAppDestinations()} */
    public void setAppDestinations(@Nullable List<Uri> appDestinations) {
        mAppDestinations = appDestinations;
    }

    /** See {@link Source#getWebDestinations()} */
    public void setWebDestinations(@Nullable List<Uri> webDestinations) {
        mWebDestinations = webDestinations;
    }

    /** Set app install attribution to the {@link Source}. */
    public void setInstallAttributed(boolean isInstallAttributed) {
        mIsInstallAttributed = isInstallAttributed;
    }

    /**
     * @return if it's a derived source, returns the ID of the source it was created from. If it is
     *     null, it is an original source.
     */
    @Nullable
    public String getParentId() {
        return mParentId;
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
     * Generates AggregatableFilterData from aggregate filter string in Source, including an entry
     * for source type.
     */
    public FilterMap getFilterData() throws JSONException {
        if (mFilterData != null) {
            return mFilterData;
        }

        if (mFilterDataString == null || mFilterDataString.isEmpty()) {
            mFilterData = new FilterMap.Builder().build();
        } else {
            mFilterData =
                    new FilterMap.Builder()
                            .buildFilterData(new JSONObject(mFilterDataString))
                            .build();
        }
        mFilterData
                .getAttributionFilterMap()
                .put("source_type", Collections.singletonList(mSourceType.getValue()));
        return mFilterData;
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
                                        new UnsignedLong(Long.valueOf(reportConfig[0])),
                                        getReportingTimeForNoising(reportConfig[1]),
                                        resolveFakeReportDestinations(reportConfig[2])))
                .collect(Collectors.toList());
    }

    private boolean isVtcDualDestinationModeWithPostInstallEnabled() {
        return mSourceType == SourceType.EVENT
                && hasWebDestinations()
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
    private List<Uri> resolveFakeReportDestinations(int destinationIdentifier) {
        if (hasAppDestinations() && hasWebDestinations()) {
            // It could be a direct destinationIdentifier == 0 check, but
            return destinationIdentifier % DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER == 0
                    ? mAppDestinations
                    : mWebDestinations;
        }

        return hasAppDestinations()
                ? mAppDestinations
                : mWebDestinations;
    }

    private boolean hasAppDestinations() {
        return mAppDestinations != null && mAppDestinations.size() > 0;
    }

    private boolean hasWebDestinations() {
        return mWebDestinations != null && mWebDestinations.size() > 0;
    }

    private static boolean areEqualNullableDestinations(List<Uri> destinations,
            List<Uri> otherDestinations) {
        if (destinations == null && otherDestinations == null) {
            return true;
        } else if (destinations == null || otherDestinations == null) {
            return false;
        } else {
            return ImmutableMultiset.copyOf(destinations).equals(
                    ImmutableMultiset.copyOf(otherDestinations));
        }
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
         * Copy builder.
         *
         * @param copyFrom copy from source
         * @return copied source
         */
        public static Builder from(Source copyFrom) {
            Builder builder = new Builder();
            builder.setId(copyFrom.mId);
            builder.setRegistrationId(copyFrom.mRegistrationId);
            builder.setAggregateSource(copyFrom.mAggregateSource);
            builder.setExpiryTime(copyFrom.mExpiryTime);
            builder.setAppDestinations(copyFrom.mAppDestinations);
            builder.setWebDestinations(copyFrom.mWebDestinations);
            builder.setSharedAggregationKeys(copyFrom.mSharedAggregationKeys);
            builder.setEventId(copyFrom.mEventId);
            builder.setRegistrant(copyFrom.mRegistrant);
            builder.setEventTime(copyFrom.mEventTime);
            builder.setPublisher(copyFrom.mPublisher);
            builder.setPublisherType(copyFrom.mPublisherType);
            builder.setInstallCooldownWindow(copyFrom.mInstallCooldownWindow);
            builder.setInstallAttributed(copyFrom.mIsInstallAttributed);
            builder.setInstallAttributionWindow(copyFrom.mInstallAttributionWindow);
            builder.setSourceType(copyFrom.mSourceType);
            builder.setAdIdPermission(copyFrom.mAdIdPermission);
            builder.setAggregateContributions(copyFrom.mAggregateContributions);
            builder.setArDebugPermission(copyFrom.mArDebugPermission);
            builder.setAttributionMode(copyFrom.mAttributionMode);
            builder.setDebugKey(copyFrom.mDebugKey);
            builder.setEventReportDedupKeys(copyFrom.mEventReportDedupKeys);
            builder.setAggregateReportDedupKeys(copyFrom.mAggregateReportDedupKeys);
            builder.setEventReportWindow(copyFrom.mEventReportWindow);
            builder.setAggregatableReportWindow(copyFrom.mAggregatableReportWindow);
            builder.setEnrollmentId(copyFrom.mEnrollmentId);
            builder.setFilterData(copyFrom.mFilterDataString);
            builder.setInstallTime(copyFrom.mInstallTime);
            builder.setIsDebugReporting(copyFrom.mIsDebugReporting);
            builder.setPriority(copyFrom.mPriority);
            builder.setStatus(copyFrom.mStatus);
            builder.setDebugJoinKey(copyFrom.mDebugJoinKey);
            return builder;
        }

        /** See {@link Source#getId()}. */
        @NonNull
        public Builder setId(@NonNull String id) {
            mBuilding.mId = id;
            return this;
        }

        /** See {@link Source#getEventId()}. */
        @NonNull
        public Builder setEventId(UnsignedLong eventId) {
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

        /** See {@link Source#getAppDestinations()}. */
        public Builder setAppDestinations(List<Uri> appDestinations) {
            Optional.ofNullable(appDestinations).ifPresent(uris -> {
                Validation.validateNotEmpty(uris);
                if (uris.size() > 1) {
                    throw new IllegalArgumentException("Received more than one app destination");
                }
                Validation.validateUri(uris.toArray(new Uri[0]));
            });
            mBuilding.mAppDestinations = appDestinations;
            return this;
        }

        /** See {@link Source#getWebDestinations()}. */
        @NonNull
        public Builder setWebDestinations(@Nullable List<Uri> webDestinations) {
            Optional.ofNullable(webDestinations).ifPresent(uris -> {
                Validation.validateNotEmpty(uris);
                Validation.validateUri(uris.toArray(new Uri[0]));
            });
            mBuilding.mWebDestinations = webDestinations;
            return this;
        }

        /** See {@link Source#getEnrollmentId()}. */
        @NonNull
        public Builder setEnrollmentId(@NonNull String enrollmentId) {
            mBuilding.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link Source#hasAdIdPermission()} */
        public Source.Builder setAdIdPermission(boolean adIdPermission) {
            mBuilding.mAdIdPermission = adIdPermission;
            return this;
        }

        /** See {@link Source#hasArDebugPermission()} */
        public Source.Builder setArDebugPermission(boolean arDebugPermission) {
            mBuilding.mArDebugPermission = arDebugPermission;
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

        /**
         * See {@link Source#getEventReportWindow()}.
         */
        public Builder setEventReportWindow(long eventReportWindow) {
            mBuilding.mEventReportWindow = eventReportWindow;
            return this;
        }

        /**
         * See {@link Source#getAggregatableReportWindow()}.
         */
        public Builder setAggregatableReportWindow(long aggregateReportWindow) {
            mBuilding.mAggregatableReportWindow = aggregateReportWindow;
            return this;
        }

        /** See {@link Source#getPriority()}. */
        @NonNull
        public Builder setPriority(long priority) {
            mBuilding.mPriority = priority;
            return this;
        }

        /** See {@link Source#getDebugKey()}. */
        public Builder setDebugKey(@Nullable UnsignedLong debugKey) {
            mBuilding.mDebugKey = debugKey;
            return this;
        }

        /** See {@link Source#isDebugReporting()}. */
        public Builder setIsDebugReporting(boolean isDebugReporting) {
            mBuilding.mIsDebugReporting = isDebugReporting;
            return this;
        }

        /** See {@link Source#getSourceType()}. */
        @NonNull
        public Builder setSourceType(@NonNull SourceType sourceType) {
            Validation.validateNonNull(sourceType);
            mBuilding.mSourceType = sourceType;
            return this;
        }

        /** See {@link Source#getEventReportDedupKeys()}. */
        @NonNull
        public Builder setEventReportDedupKeys(@Nullable List<UnsignedLong> mEventReportDedupKeys) {
            mBuilding.mEventReportDedupKeys = mEventReportDedupKeys;
            return this;
        }

        /** See {@link Source#getAggregateReportDedupKeys()}. */
        @NonNull
        public Builder setAggregateReportDedupKeys(
                @NonNull List<UnsignedLong> mAggregateReportDedupKeys) {
            mBuilding.mAggregateReportDedupKeys = mAggregateReportDedupKeys;
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

        /** See {@link Source#getFilterDataString()}. */
        public Builder setFilterData(@Nullable String filterMap) {
            mBuilding.mFilterDataString = filterMap;
            return this;
        }

        /** See {@link Source#getAggregateSource()} */
        @NonNull
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

        /** See {@link Source#getRegistrationId()} */
        @NonNull
        public Builder setRegistrationId(@Nullable String registrationId) {
            mBuilding.mRegistrationId = registrationId;
            return this;
        }

        /** See {@link Source#getSharedAggregationKeys()} */
        @NonNull
        public Builder setSharedAggregationKeys(@Nullable String sharedAggregationKeys) {
            mBuilding.mSharedAggregationKeys = sharedAggregationKeys;
            return this;
        }

        /** See {@link Source#getInstallTime()} */
        @NonNull
        public Builder setInstallTime(@Nullable Long installTime) {
            mBuilding.mInstallTime = installTime;
            return this;
        }

        /** See {@link Source#getParentId()} */
        @NonNull
        public Builder setParentId(@Nullable String parentId) {
            mBuilding.mParentId = parentId;
            return this;
        }

        /** See {@link Source#getAggregatableAttributionSource()} */
        @NonNull
        public Builder setAggregatableAttributionSource(
                @Nullable AggregatableAttributionSource aggregatableAttributionSource) {
            mBuilding.mAggregatableAttributionSource =
                    Optional.ofNullable(aggregatableAttributionSource);
            return this;
        }

        /** See {@link Source#getDebugJoinKey()} */
        @NonNull
        public Builder setDebugJoinKey(@Nullable String debugJoinKey) {
            mBuilding.mDebugJoinKey = debugJoinKey;
            return this;
        }

        /** Build the {@link Source}. */
        @NonNull
        public Source build() {
            Validation.validateNonNull(
                    mBuilding.mPublisher,
                    mBuilding.mEnrollmentId,
                    mBuilding.mRegistrant,
                    mBuilding.mSourceType,
                    mBuilding.mAggregateReportDedupKeys,
                    mBuilding.mEventReportDedupKeys);

            //if (mBuilding.mAppDestinations == null && mBuilding.mWebDestinations == null) {
            //    throw new IllegalArgumentException("At least one destination is required");
            //}

            return mBuilding;
        }
    }
}
