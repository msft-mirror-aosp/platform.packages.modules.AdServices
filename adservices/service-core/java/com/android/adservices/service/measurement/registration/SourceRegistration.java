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

import com.android.adservices.service.measurement.validation.Validation;

import java.util.Objects;
import java.util.Optional;

/**
 * A registration for an attribution source.
 */
public final class SourceRegistration {
    private final Uri mTopOrigin;
    private final Uri mReportingOrigin;
    private final Uri mAppDestination;
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
            @Nullable Uri appDestination,
            @Nullable Uri webDestination,
            long sourceEventId,
            long expiry,
            long sourcePriority,
            long installAttributionWindow,
            long installCooldownWindow,
            @Nullable String aggregateSource,
            @Nullable String aggregateFilterData) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mAppDestination = appDestination;
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
                && Objects.equals(mAppDestination, that.mAppDestination)
                && Objects.equals(mWebDestination, that.mWebDestination)
                && Objects.equals(mAggregateSource, that.mAggregateSource)
                && Objects.equals(mAggregateFilterData, that.mAggregateFilterData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTopOrigin,
                mReportingOrigin,
                mAppDestination,
                mWebDestination,
                mSourceEventId,
                mExpiry,
                mSourcePriority,
                mInstallAttributionWindow,
                mInstallCooldownWindow,
                mAggregateSource,
                mAggregateFilterData);
    }

    /** Top level origin. */
    @NonNull
    public Uri getTopOrigin() {
        return mTopOrigin;
    }

    /** Reporting origin. */
    @NonNull
    public Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /** OS (app) destination Uri. */
    @Nullable
    public Uri getAppDestination() {
        return mAppDestination;
    }

    /** Web destination Uri. */
    @Nullable
    public Uri getWebDestination() {
        return mWebDestination;
    }

    /** Source event id. */
    @NonNull
    public long getSourceEventId() {
        return mSourceEventId;
    }

    /** Expiration. */
    @NonNull
    public long getExpiry() {
        return mExpiry;
    }

    /** Source priority. */
    @NonNull
    public long getSourcePriority() {
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
        private Uri mAppDestination;
        private Uri mWebDestination;
        private long mSourceEventId;
        private long mExpiry;
        private long mSourcePriority;
        private long mInstallAttributionWindow;
        private long mInstallCooldownWindow;
        private String mAggregateSource;
        private String mAggregateFilterData;

        public Builder() {
            mExpiry = MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
            mInstallAttributionWindow = MAX_INSTALL_ATTRIBUTION_WINDOW;
            mInstallCooldownWindow = MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
        }

        /** See {@link SourceRegistration#getTopOrigin}. */
        @NonNull
        public Builder setTopOrigin(@NonNull Uri origin) {
            Validation.validateUri(origin);
            mTopOrigin = origin;
            return this;
        }

        /** See {@link SourceRegistration#getReportingOrigin}. */
        @NonNull
        public Builder setReportingOrigin(@NonNull Uri origin) {
            Validation.validateUri(origin);
            mReportingOrigin = origin;
            return this;
        }

        /**
         * See {@link SourceRegistration#getAppDestination}. At least one of destination or web
         * destination is required.
         */
        @NonNull
        public Builder setAppDestination(@Nullable Uri appDestination) {
            Optional.ofNullable(appDestination).ifPresent(Validation::validateUri);
            mAppDestination = appDestination;
            return this;
        }

        /**
         * See {@link SourceRegistration#getWebDestination()}. At least one of destination or web
         * destination is required.
         */
        @NonNull
        public Builder setWebDestination(@Nullable Uri webDestination) {
            Optional.ofNullable(webDestination).ifPresent(Validation::validateUri);
            mWebDestination = webDestination;
            return this;
        }

        /** See {@link SourceRegistration#getSourceEventId}. */
        @NonNull
        public Builder setSourceEventId(long sourceEventId) {
            mSourceEventId = sourceEventId;
            return this;
        }

        /** See {@link SourceRegistration#getExpiry}. */
        @NonNull
        public Builder setExpiry(long expiry) {
            mExpiry = expiry;
            return this;
        }

        /** See {@link SourceRegistration#getSourcePriority}. */
        @NonNull
        public Builder setSourcePriority(long priority) {
            mSourcePriority = priority;
            return this;
        }

        /** See {@link SourceRegistration#getInstallAttributionWindow()}. */
        @NonNull
        public Builder setInstallAttributionWindow(long installAttributionWindow) {
            mInstallAttributionWindow = installAttributionWindow;
            return this;
        }

        /** See {@link SourceRegistration#getInstallCooldownWindow()}. */
        @NonNull
        public Builder setInstallCooldownWindow(long installCooldownWindow) {
            mInstallCooldownWindow = installCooldownWindow;
            return this;
        }

        /** See {@link SourceRegistration#getAggregateSource()}. */
        @NonNull
        public Builder setAggregateSource(@Nullable String aggregateSource) {
            mAggregateSource = aggregateSource;
            return this;
        }

        /** See {@link SourceRegistration#getAggregateFilterData()}. */
        @NonNull
        public Builder setAggregateFilterData(@Nullable String aggregateFilterData) {
            mAggregateFilterData = aggregateFilterData;
            return this;
        }

        /** Build the SourceRegistration. */
        @NonNull
        public SourceRegistration build() {
            Validation.validateNonNull(mTopOrigin, mReportingOrigin);

            if (mAppDestination == null && mWebDestination == null) {
                throw new IllegalArgumentException(
                        "At least one of destination or web destination is required.");
            }

            return new SourceRegistration(
                    mTopOrigin,
                    mReportingOrigin,
                    mAppDestination,
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
