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
package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.measurement.PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import java.util.Objects;

/**
 * A registration for an attribution source.
 */
public final class SourceRegistration {
    private final Uri mTopOrigin;
    private final Uri mReportingOrigin;
    private final Uri mDestination;
    private final Uri mWebDestination;
    private final long mSourceEventId;
    private final long mExpiry;
    private final long mSourcePriority;
    private final long mInstallAttributionWindow;
    private final long mInstallCooldownWindow;
    private final String mAggregateSource;
    private final String mAggregateFilterData;

    /** Create a new source registration. */
    private SourceRegistration(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @Nullable Uri destination,
            @Nullable Uri webDestination,
            long sourceEventId,
            long expiry,
            long sourcePriority,
            long installAttributionWindow,
            long installCooldownWindow,
            String aggregateSource,
            String aggregateFilterData) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mDestination = destination;
        mWebDestination = webDestination;
        mSourceEventId = sourceEventId;
        mExpiry = expiry;
        mSourcePriority = sourcePriority;
        mInstallAttributionWindow = installAttributionWindow;
        mInstallCooldownWindow = installCooldownWindow;
        mAggregateSource = aggregateSource;
        mAggregateFilterData = aggregateFilterData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceRegistration)) return false;
        SourceRegistration that = (SourceRegistration) o;
        return mSourceEventId == that.mSourceEventId
                && mExpiry == that.mExpiry
                && mSourcePriority == that.mSourcePriority
                && mInstallAttributionWindow == that.mInstallAttributionWindow
                && mInstallCooldownWindow == that.mInstallCooldownWindow
                && Objects.equals(mTopOrigin, that.mTopOrigin)
                && Objects.equals(mReportingOrigin, that.mReportingOrigin)
                && Objects.equals(mDestination, that.mDestination)
                && Objects.equals(mWebDestination, that.mWebDestination)
                && Objects.equals(mAggregateSource, that.mAggregateSource)
                && Objects.equals(mAggregateFilterData, that.mAggregateFilterData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTopOrigin,
                mReportingOrigin,
                mDestination,
                mWebDestination,
                mSourceEventId,
                mExpiry,
                mSourcePriority,
                mInstallAttributionWindow,
                mInstallCooldownWindow,
                mAggregateSource,
                mAggregateFilterData);
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

    /** OS (app) destination Uri. */
    public @Nullable Uri getDestination() {
        return mDestination;
    }

    /** Web destination Uri. */
    public @Nullable Uri getWebDestination() {
        return mWebDestination;
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
     * Install attribution window.
     */
    public long getInstallAttributionWindow() {
        return mInstallAttributionWindow;
    }

    /**
     * Install cooldown window.
     */
    public long getInstallCooldownWindow() {
        return mInstallCooldownWindow;
    }

    /**
     * Aggregate source used to generate aggregate report.
     */
    public String getAggregateSource() {
        return mAggregateSource;
    }

    /**
     * Aggregate filter data used to generate aggregate report.
     */
    public String getAggregateFilterData() {
        return mAggregateFilterData;
    }

    /**
     * A builder for {@link SourceRegistration}.
     */
    public static final class Builder {
        private Uri mTopOrigin;
        private Uri mReportingOrigin;
        private Uri mDestination;
        private Uri mWebDestination;
        private long mSourceEventId;
        private long mExpiry;
        private long mSourcePriority;
        private long mInstallAttributionWindow;
        private long mInstallCooldownWindow;
        private String mAggregateSource;
        private String mAggregateFilterData;

        public Builder() {
            mTopOrigin = Uri.EMPTY;
            mReportingOrigin = Uri.EMPTY;
            mDestination = Uri.EMPTY;
            mWebDestination = Uri.EMPTY;
            mExpiry = MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
            mInstallAttributionWindow = MAX_INSTALL_ATTRIBUTION_WINDOW;
            mInstallCooldownWindow = MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
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
         * See {@link SourceRegistration#getDestination}. At least one of destination or web
         * destination is required.
         */
        public @NonNull Builder setDestination(@Nullable Uri destination) {
            mDestination = destination;
            return this;
        }

        /**
         * See {@link SourceRegistration#getWebDestination()}. At least one of destination or web
         * destination is required.
         */
        public @NonNull Builder setWebDestination(@Nullable Uri webDestination) {
            mWebDestination = webDestination;
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
         * See {@link SourceRegistration#getInstallAttributionWindow()}.
         */
        public @NonNull Builder setInstallAttributionWindow(long installAttributionWindow) {
            mInstallAttributionWindow = installAttributionWindow;
            return this;
        }

        /**
         * See {@link SourceRegistration#getInstallCooldownWindow()}.
         */
        public @NonNull Builder setInstallCooldownWindow(long installCooldownWindow) {
            mInstallCooldownWindow = installCooldownWindow;
            return this;
        }

        /**
         * See {@link SourceRegistration#getAggregateSource()}.
         */
        public Builder setAggregateSource(String aggregateSource) {
            mAggregateSource = aggregateSource;
            return this;
        }

        /**
         * See {@link SourceRegistration#getAggregateFilterData()}.
         */
        public Builder setAggregateFilterData(String aggregateFilterData) {
            mAggregateFilterData = aggregateFilterData;
            return this;
        }

        /**
         * Build the SourceRegistration.
         */
        public @NonNull SourceRegistration build() {
            if (mTopOrigin == null || mReportingOrigin == null) {
                throw new IllegalArgumentException("uninitialized fields");
            }

            if (mDestination == null && mWebDestination == null) {
                throw new IllegalArgumentException(
                        "At least one of destination or web destination is required.");
            }

            return new SourceRegistration(
                    mTopOrigin,
                    mReportingOrigin,
                    mDestination,
                    mWebDestination,
                    mSourceEventId,
                    mExpiry,
                    mSourcePriority,
                    mInstallAttributionWindow,
                    mInstallCooldownWindow,
                    mAggregateSource,
                    mAggregateFilterData);
        }
    }
}
