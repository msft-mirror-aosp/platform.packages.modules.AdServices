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
package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Objects;

/** Debug Report. */
public final class DebugReport {
    private final String mId;
    private final String mType;
    private final String mBody;
    private final String mEnrollmentId;

    /** Create a new debug report object. */
    private DebugReport(
            @Nullable String id,
            @NonNull String type,
            @NonNull String body,
            @NonNull String enrollmentId) {
        mId = id;
        mType = type;
        mBody = body;
        mEnrollmentId = enrollmentId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DebugReport)) {
            return false;
        }
        DebugReport key = (DebugReport) obj;
        return Objects.equals(mType, key.mType)
                && Objects.equals(mBody, key.mBody)
                && Objects.equals(mEnrollmentId, key.mEnrollmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mBody, mEnrollmentId);
    }

    /** Unique identifier for the {@link DebugReport}. */
    public String getId() {
        return mId;
    }

    /** Type of debug report. */
    public String getType() {
        return mType;
    }

    /** Body of debug report. */
    public String getBody() {
        return mBody;
    }

    /** AdTech enrollment ID. */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** A builder for {@link DebugReport}. */
    public static final class Builder {
        private String mId;
        private String mType;
        private String mBody;
        private String mEnrollmentId;

        public Builder() {}

        /** See {@link DebugReport#getId()}. */
        public Builder setId(String id) {
            mId = id;
            return this;
        }

        /** See {@link DebugReport#getType}. */
        public @NonNull Builder setType(@NonNull String type) {
            mType = type;
            return this;
        }

        /** See {@link DebugReport#getBody}. */
        public @NonNull Builder setBody(@NonNull String body) {
            mBody = body;
            return this;
        }

        /** See {@link DebugReport#getEnrollmentId()} ()}. */
        @NonNull
        public Builder setEnrollmentId(String enrollmentId) {
            mEnrollmentId = enrollmentId;
            return this;
        }

        /** Build the DebugReport. */
        public @NonNull DebugReport build() {
            if (mType == null || mBody == null) {
                throw new IllegalArgumentException("Uninitialized fields");
            }
            return new DebugReport(mId, mType, mBody, mEnrollmentId);
        }
    }
}
