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
 * A registration for an attribution source.
 */
public final class SourceRegistration {
    private final Uri mTopOrigin;
    private final Uri mReportingOrigin;
    private final Uri mDestination;
    private final long mSourceEventId;
    private final long mExpiry;
    private final long mSourcePriority;

    /**
     * Create a new source registration.
     */
    private SourceRegistration(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @NonNull Uri destination,
            long sourceEventId,
            long expiry,
            long sourcePriority) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mDestination = destination;
        mSourceEventId = sourceEventId;
        mExpiry = expiry;
        mSourcePriority = sourcePriority;
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
     * Destination Uri.
     */
    public @NonNull Uri getDestination() {
        return mDestination;
    }

    /**
     * Source event id.
     */
    public @NonNull long getSourceEventId() {
        return mSourceEventId;
    }

    /**
     * Expiration.
     */
    public @NonNull long getExpiry() {
        return mExpiry;
    }

    /**
     * Source priority.
     */
    public @NonNull long getSourcePriority() {
        return mSourcePriority;
    }

    /**
     * A builder for {@link SourceRegistration}.
     */
    public static final class Builder {
        private Uri mTopOrigin;
        private Uri mReportingOrigin;
        private Uri mDestination;
        private long mSourceEventId;
        private long mExpiry;
        private long mSourcePriority;

        public Builder() {
            mTopOrigin = Uri.EMPTY;
            mReportingOrigin = Uri.EMPTY;
            mDestination = Uri.EMPTY;
        }

        /**
         * See {@link SourceRegistration#getTopOrigin}.
         */
        public @NonNull Builder setTopOrigin(@NonNull Uri origin) {
            mTopOrigin = origin;
            return this;
        }

        /**
         * See {@link SourceRegistration#getReportingOrigin}.
         */
        public @NonNull Builder setReportingOrigin(@NonNull Uri origin) {
            mReportingOrigin = origin;
            return this;
        }

        /**
         * See {@link SourceRegistration#getDestination}.
         */
        public @NonNull Builder setDestination(@NonNull Uri destination) {
            mDestination = destination;
            return this;
        }

        /**
         * See {@link SourceRegistration#getSourceEventId}.
         */
        public @NonNull Builder setSourceEventId(long sourceEventId) {
            mSourceEventId = sourceEventId;
            return this;
        }

        /**
         * See {@link SourceRegistration#getExpiry}.
         */
        public @NonNull Builder setExpiry(long expiry) {
            mExpiry = expiry;
            return this;
        }

        /**
         * See {@link SourceRegistration#getSourcePriority}.
         */
        public @NonNull Builder setSourcePriority(long priority) {
            mSourcePriority = priority;
            return this;
        }

        /**
         * Build the SourceRegistration.
         */
        public @NonNull SourceRegistration build() {
            if (mTopOrigin == null
                    || mReportingOrigin == null
                    || mDestination == null) {
                throw new IllegalArgumentException("uinitialized fields");
            }
            return new SourceRegistration(
                    mTopOrigin,
                    mReportingOrigin,
                    mDestination,
                    mSourceEventId,
                    mExpiry,
                    mSourcePriority);
        }
    }
}
