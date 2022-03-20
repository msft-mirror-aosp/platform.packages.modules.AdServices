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

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * POJO for Source.
 */
public class Source {

    private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);

    private String mId;
    private long mEventId;
    private Uri mAttributionSource;
    private Uri mAttributionDestination;
    private Uri mReportTo;
    private Uri mRegisterer;
    private SourceType mSourceType;
    private long mPriority;
    private @Status int mStatus;
    private long mEventTime;
    private long mExpiryTime;
    private List<Long> mDedupKeys;
    private @AttributionMode int mAttributionMode;

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
        EVENT,
        NAVIGATION,
    }

    private Source() {
        mDedupKeys = new ArrayList<>();
        mStatus = Status.ACTIVE;
        mSourceType = SourceType.EVENT;
        mAttributionMode = AttributionMode.UNASSIGNED;
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

    private ImmutableList<Long> getEarlyReportingWindows() {
        if (mSourceType == SourceType.EVENT) {
            return ImmutableList.of();
        }
        List<Long> windowList = new ArrayList<>();
        for (long windowDelta : PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS) {
            long window = mEventTime + windowDelta;
            if (mExpiryTime <= window) {
                continue;
            }
            windowList.add(window);
        }
        return ImmutableList.copyOf(windowList);
    }

    private long getReportingTimeByIndex(int windowIndex) {
        List<Long> windowList = getEarlyReportingWindows();
        return windowIndex < windowList.size()
                ? windowList.get(windowIndex) + ONE_HOUR_IN_MILLIS :
                mExpiryTime + ONE_HOUR_IN_MILLIS;
    }

    private int getReportingWindowCount() {
        // Early Count + expiry
        return getEarlyReportingWindows().size() + 1;
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
     * @return Random noise rate for {@link Trigger} metadata
     */
    public double getTriggerDataNoiseRate() {
        return mSourceType == Source.SourceType.EVENT
                ? PrivacyParams.EVENT_RANDOM_TRIGGER_DATA_NOISE :
                PrivacyParams.NAVIGATION_RANDOM_TRIGGER_DATA_NOISE;
    }

    /**
     * @return Maximum number of reports allowed
     */
    public int getMaxReportCount() {
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_SOURCE_MAX_REPORTS :
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
    }

    /**
     * @return Probability of selecting random state for attribution
     */
    public double getRandomAttributionProbability() {
        return mSourceType == SourceType.EVENT
                ? PrivacyParams.EVENT_RANDOM_ATTRIBUTION_STATE_PROBABILITY :
                PrivacyParams.NAVIGATION_RANDOM_ATTRIBUTION_STATE_PROBABILITY;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Source)) {
            return false;
        }
        Source source = (Source) obj;
        return Objects.equals(mId, source.mId)
                && Objects.equals(mAttributionSource, source.mAttributionSource)
                && Objects.equals(mAttributionDestination, source.mAttributionDestination)
                && Objects.equals(mReportTo, source.mReportTo)
                && mPriority == source.mPriority
                && mStatus == source.mStatus
                && mExpiryTime == source.mExpiryTime
                && mEventTime == source.mEventTime
                && mEventId == source.mEventId
                && mSourceType == source.mSourceType
                && Objects.equals(mDedupKeys, source.mDedupKeys)
                && Objects.equals(mRegisterer, source.mRegisterer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mAttributionSource, mAttributionDestination, mReportTo, mPriority,
                mStatus, mExpiryTime, mEventTime, mEventId, mSourceType, mDedupKeys);
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
        List<Long> reportingWindows = getEarlyReportingWindows();
        for (Long window: reportingWindows) {
            if (triggerTime < window) {
                return window + ONE_HOUR_IN_MILLIS;
            }
        }
        return mExpiryTime + ONE_HOUR_IN_MILLIS;
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
        int triggerDataCardinality = getTriggerDataCardinality();
        // Get total possible combinations
        int numCombinations = Combinatorics.getNumberOfStarsAndBarsSequences(
                /*numStars=*/getMaxReportCount(),
                /*numBars=*/triggerDataCardinality * getReportingWindowCount());
        // Choose a sequence index
        int sequenceIndex = rand.nextInt(numCombinations);
        List<FakeReport> fakeReports = generateFakeReports(sequenceIndex);
        mAttributionMode = fakeReports.isEmpty() ? AttributionMode.NEVER : AttributionMode.FALSELY;
        return fakeReports;
    }

    @VisibleForTesting
    List<FakeReport> generateFakeReports(int sequenceIndex) {
        List<FakeReport> fakeReports = new ArrayList<>();
        int triggerDataCardinality = getTriggerDataCardinality();
        // Get the configuration for the sequenceIndex
        int[] starIndices = Combinatorics.getStarIndices(
                /*numStars=*/getMaxReportCount(),
                /*sequenceIndex=*/sequenceIndex);
        int[] barsPrecedingEachStar = Combinatorics.getBarsPrecedingEachStar(starIndices);
        // Generate fake reports
        // Stars: number of reports
        // Bars: (Number of windows) * (Trigger Data Cardinality)
        for (int numBars : barsPrecedingEachStar) {
            if (numBars == 0) {
                continue;
            }
            int windowIndex = (numBars - 1) / triggerDataCardinality;
            int triggerData = (numBars - 1) % triggerDataCardinality;
            fakeReports.add(new FakeReport(triggerData, getReportingTimeByIndex(windowIndex)));
        }
        return fakeReports;
    }

    /**
     * Unique identifier for the {@link Source}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Identifier provided by the registerer.
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
     * Reporting destination for the generated reports.
     */
    public Uri getReportTo() {
        return mReportTo;
    }

    /**
     * Uri which registered the {@link Source}.
     */
    public Uri getAttributionSource() {
        return mAttributionSource;
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
     * Registerer of this source, primarily an App.
     */
    public Uri getRegisterer() {
        return mRegisterer;
    }

    /**
     * Selected mode for attribution. Values: Truthfully, Never, Falsely.
     */
    public @AttributionMode int getAttributionMode() {
        return mAttributionMode;
    }

    /**
     * Set the status.
     */
    public void setStatus(@Status int status) {
        mStatus = status;
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
         * See {@link Source#getAttributionSource()}.
         */
        public Builder setAttributionSource(Uri attributionSource) {
            mBuilding.mAttributionSource = attributionSource;
            return this;
        }

        /**
         * See {@link Source#getAttributionDestination()}.
         */

        public Builder setAttributionDestination(Uri attributionDestination) {
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link Source#getReportTo()}.
         */
        public Builder setReportTo(Uri reportTo) {
            mBuilding.mReportTo = reportTo;
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
         * See {@link Source#getRegisterer()}
         */
        public Builder setRegisterer(Uri registerer) {
            mBuilding.mRegisterer = registerer;
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
         * Build the {@link Source}.
         */
        public Source build() {
            return mBuilding;
        }
    }
}
