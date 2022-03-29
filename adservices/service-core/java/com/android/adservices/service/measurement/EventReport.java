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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * POJO for EventReport.
 */
public class EventReport {

    private String mId;
    private long mSourceId;
    private long mReportTime;
    private long mTriggerTime;
    private long mTriggerPriority;
    private Uri mAttributionDestination;
    private Uri mReportTo;
    private long mTriggerData;
    private Long mTriggerDedupKey;
    private @Status int mStatus;

    @IntDef(value = {
            Status.PENDING,
            Status.DELIVERED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int PENDING = 0;
        int DELIVERED = 1;
    }

    private EventReport() {
        mTriggerDedupKey = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EventReport)) {
            return false;
        }
        EventReport eventReport = (EventReport) obj;
        return mStatus == eventReport.mStatus
                && mReportTime == eventReport.mReportTime
                && Objects.equals(mAttributionDestination, eventReport.mAttributionDestination)
                && Objects.equals(mReportTo, eventReport.mReportTo)
                && mTriggerTime == eventReport.mTriggerTime
                && mTriggerData == eventReport.mTriggerData
                && mSourceId == eventReport.mSourceId
                && mTriggerPriority == eventReport.mTriggerPriority
                && Objects.equals(mTriggerDedupKey, eventReport.mTriggerDedupKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStatus, mReportTime, mAttributionDestination, mReportTo, mTriggerTime,
                mTriggerData, mSourceId, mTriggerPriority, mTriggerDedupKey);
    }

    /**
     * Unique identifier for the report.
     */
    public String getId() {
        return mId;
    }

    /**
     * Identifier of the associated {@link Source} event.
     */
    public long getSourceId() {
        return mSourceId;
    }

    /**
     * Scheduled time for the report to be sent.
     */
    public long getReportTime() {
        return mReportTime;
    }

    /**
     * TriggerTime of the associated {@link Trigger}.
     */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /**
     * Priority of the associated {@link Trigger}.
     */
    public long getTriggerPriority() {
        return mTriggerPriority;
    }

    /**
     * AttributionDestination of the {@link Source} and {@link Trigger}.
     */
    public Uri getAttributionDestination() {
        return mAttributionDestination;
    }

    /**
     * Reporting endpoint for the report.
     */
    public Uri getReportTo() {
        return mReportTo;
    }

    /**
     * Metadata for the report.
     */
    public long getTriggerData() {
        return mTriggerData;
    }

    /**
     * Deduplication key of the associated {@link Trigger}
     */
    public Long getTriggerDedupKey() {
        return mTriggerDedupKey;
    }

    /**
     * Current {@link Status} of the report.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Builder for {@link EventReport}
     */
    public static final class Builder {

        private final EventReport mBuilding;

        public Builder() {
            mBuilding = new EventReport();
        }

        /**
         * See {@link EventReport#getId()}
         */
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /**
         * See {@link EventReport#getSourceId()}
         */
        public Builder setSourceId(long sourceId) {
            mBuilding.mSourceId = sourceId;
            return this;
        }

        /**
         * See {@link EventReport#getReportTo()}
         */
        public Builder setReportTo(Uri reportTo) {
            mBuilding.mReportTo = reportTo;
            return this;
        }

        /**
         * See {@link EventReport#getAttributionDestination()}
         */
        public Builder setAttributionDestination(Uri attributionDestination) {
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerTime()}
         */
        public Builder setTriggerTime(long triggerTime) {
            mBuilding.mTriggerTime = triggerTime;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerData()}
         */
        public Builder setTriggerData(long triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerPriority()}
         */
        public Builder setTriggerPriority(long triggerPriority) {
            mBuilding.mTriggerPriority = triggerPriority;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerDedupKey()}
         */
        public Builder setTriggerDedupKey(Long triggerDedupKey) {
            mBuilding.mTriggerDedupKey = triggerDedupKey;
            return this;
        }

        /**
         * See {@link EventReport#getReportTime()}
         */
        public Builder setReportTime(long reportTime) {
            mBuilding.mReportTime = reportTime;
            return this;
        }

        /**
         * See {@link EventReport#getStatus()}
         */
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /**
         * Populates fields using {@link Source} and {@link Trigger}.
         */
        public Builder populateFromSourceAndTrigger(Source source, Trigger trigger) {
            mBuilding.mTriggerPriority = trigger.getPriority();
            mBuilding.mTriggerDedupKey = trigger.getDedupKey();
            mBuilding.mTriggerData = trigger.getRandomizedTriggerData(source);
            mBuilding.mTriggerTime = trigger.getTriggerTime();
            mBuilding.mSourceId = source.getEventId();
            mBuilding.mReportTo = source.getReportTo();
            mBuilding.mStatus = Status.PENDING;
            mBuilding.mAttributionDestination = source.getAttributionDestination();
            mBuilding.mReportTime = source.getReportingTime(trigger.getTriggerTime());
            return this;
        }

        /**
         * Build the {@link EventReport}.
         */
        public EventReport build() {
            return mBuilding;
        }
    }
}
