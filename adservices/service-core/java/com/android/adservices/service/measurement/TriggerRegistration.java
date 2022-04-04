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

import android.annotation.NonNull;
import android.net.Uri;


/**
 * A registration for a trigger of attribution.
 */
public final class TriggerRegistration {
    private final Uri mTopOrigin;
    private final Uri mReportingOrigin;
    private final long mTriggerData;
    private final long mTriggerPriority;
    private final long mDeduplicationKey;

    /**
     * Create a trigger registration.
     */
    private TriggerRegistration(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            long triggerData,
            long triggerPriority,
            long deduplicationKey) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mTriggerData = triggerData;
        mTriggerPriority = triggerPriority;
        mDeduplicationKey = deduplicationKey;
    }

    /**
     * Top level origin.
     */
    public @NonNull Uri getTopOrigin() {
        return mTopOrigin;
    }

    /**
     * Reporting origin.
     */
    public @NonNull Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /**
     * Trigger data.
     */
    public @NonNull long getTriggerData() {
        return mTriggerData;
    }

    /**
     * Trigger priority.
     */
    public @NonNull long getTriggerPriority() {
        return mTriggerPriority;
    }

    /**
     * De-dup key.
     */
    public @NonNull long getDeduplicationKey() {
        return mDeduplicationKey;
    }

    /**
     * A builder for {@link TriggerRegistration}.
     */
    public static final class Builder {
        private Uri mTopOrigin;
        private Uri mReportingOrigin;
        private long mTriggerData;
        private long mTriggerPriority;
        private long mDeduplicationKey;

        public Builder() {
            mTopOrigin = Uri.EMPTY;
            mReportingOrigin = Uri.EMPTY;
        }

        /**
         * See {@link TriggerRegistration#getTopOrigin}.
         */
        public @NonNull Builder setTopOrigin(@NonNull Uri origin) {
            mTopOrigin = origin;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getReportingOrigin}.
         */
        public @NonNull Builder setReportingOrigin(@NonNull Uri origin) {
            mReportingOrigin = origin;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getTriggerData}.
         */
        public @NonNull Builder setTriggerData(long data) {
            mTriggerData = data;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getTriggerPriority}.
         */
        public @NonNull Builder setTriggerPriority(long priority) {
            mTriggerPriority = priority;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getDeduplicationKey}.
         */
        public @NonNull Builder setDeduplicationKey(long key) {
            mDeduplicationKey = key;
            return this;
        }

        /**
         * Build the TriggerRegistration.
         */
        public @NonNull TriggerRegistration build() {
            if (mTopOrigin == null
                    || mReportingOrigin == null) {
                throw new IllegalArgumentException("uninitialized field");
            }
            return new TriggerRegistration(
                    mTopOrigin,
                    mReportingOrigin,
                    mTriggerData,
                    mTriggerPriority,
                    mDeduplicationKey);
        }
    }
}
