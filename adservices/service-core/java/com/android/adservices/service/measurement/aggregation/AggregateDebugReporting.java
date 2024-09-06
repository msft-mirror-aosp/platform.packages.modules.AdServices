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
import android.net.Uri;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** POJO for AggregateDebugReporting. */
public class AggregateDebugReporting {

    private final int mBudget;
    private final BigInteger mKeyPiece;
    private final List<AggregateDebugReportData> mAggregateDebugReportDataList;
    private final Uri mAggregationCoordinatorOrigin;

    private AggregateDebugReporting(@NonNull AggregateDebugReporting.Builder builder) {
        mBudget = builder.mBudget;
        mKeyPiece = builder.mKeyPiece;
        mAggregateDebugReportDataList = builder.mAggregateDebugReportDataList;
        mAggregationCoordinatorOrigin = builder.mAggregationCoordinatorOrigin;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateDebugReporting aggregateDebugReporting)) {
            return false;
        }
        return mBudget == aggregateDebugReporting.mBudget
                && Objects.equals(mKeyPiece, aggregateDebugReporting.mKeyPiece)
                && Objects.equals(
                        mAggregateDebugReportDataList,
                        aggregateDebugReporting.mAggregateDebugReportDataList)
                && Objects.equals(
                        mAggregationCoordinatorOrigin,
                        aggregateDebugReporting.mAggregationCoordinatorOrigin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mBudget, mKeyPiece, mAggregateDebugReportDataList, mAggregationCoordinatorOrigin);
    }

    /** Returns the value of the aggregate_debug_report key budget. */
    public int getBudget() {
        return mBudget;
    }

    /** Returns the value of the aggregate_debug_report key key_piece. */
    public BigInteger getKeyPiece() {
        return mKeyPiece;
    }

    /** Returns the value of the aggregate_debug_report key data. */
    public List<AggregateDebugReportData> getAggregateDebugReportDataList() {
        return mAggregateDebugReportDataList;
    }

    /** Returns the value of the aggregate_debug_report key aggregation_coordinator_origin. */
    public Uri getAggregationCoordinatorOrigin() {
        return mAggregationCoordinatorOrigin;
    }

    /** Builder for {@link AggregateDebugReporting}. */
    public static final class Builder {
        private int mBudget;
        private BigInteger mKeyPiece;
        private List<AggregateDebugReportData> mAggregateDebugReportDataList;
        private Uri mAggregationCoordinatorOrigin;

        public Builder(
                int budget,
                BigInteger keyPiece,
                List<AggregateDebugReportData> aggregateDebugReportDataList,
                Uri aggregationCoordinatorOrigin) {
            mBudget = budget;
            mKeyPiece = keyPiece;
            mAggregateDebugReportDataList = aggregateDebugReportDataList;
            mAggregationCoordinatorOrigin = aggregationCoordinatorOrigin;
        }

        /** Build the {@link AggregateDebugReporting} */
        @NonNull
        public AggregateDebugReporting build() {
            return new AggregateDebugReporting(this);
        }
    }
}
