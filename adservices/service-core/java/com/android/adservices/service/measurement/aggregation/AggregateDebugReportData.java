/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.measurement.aggregation;

import android.annotation.NonNull;

import com.android.adservices.service.measurement.util.Validation;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;

/** POJO for AggregateDebugReportData. */
public class AggregateDebugReportData {

    private final Set<String> mReportType;
    private final BigInteger mKeyPiece;
    private final int mValue;

    private AggregateDebugReportData(@NonNull AggregateDebugReportData.Builder builder) {
        mReportType = builder.mReportType;
        mKeyPiece = builder.mKeyPiece;
        mValue = builder.mValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateDebugReportData aggregateDebugReportData)) {
            return false;
        }
        return Objects.equals(mReportType, aggregateDebugReportData.mReportType)
                && Objects.equals(mKeyPiece, aggregateDebugReportData.mKeyPiece)
                && mValue == aggregateDebugReportData.mValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mReportType, mKeyPiece, mValue);
    }

    /** Returns the value of key report_type. */
    public Set<String> getReportType() {
        return mReportType;
    }

    /** Returns the value of key key_piece. */
    public BigInteger getKeyPiece() {
        return mKeyPiece;
    }

    /** Returns the value of key value. */
    public int getValue() {
        return mValue;
    }

    /** Builder for {@link AggregateDebugReportData}. */
    public static final class Builder {
        private Set<String> mReportType;
        private BigInteger mKeyPiece;
        private int mValue;

        public Builder(Set<String> reportType, BigInteger keyPiece, int value) {
            mReportType = reportType;
            mKeyPiece = keyPiece;
            mValue = value;
        }

        /** Build the {@link AggregateDebugReportData} */
        @NonNull
        public AggregateDebugReportData build() {
            Validation.validateNonNull(mReportType, mKeyPiece);
            return new AggregateDebugReportData(this);
        }
    }
}
