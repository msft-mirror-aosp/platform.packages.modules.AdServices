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

import android.annotation.Nullable;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.Filter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

/** Aggregatable Bucket containing the bucket name and filters info. */
public class AggregatableBucket {
    @Nullable private final String mBucket;
    @Nullable private final List<FilterMap> mFilterSet;
    @Nullable private final List<FilterMap> mNotFilterSet;

    public AggregatableBucket(JSONObject bucketObj, Flags flags) throws JSONException {
        mBucket =
                !bucketObj.isNull(AggregatableBucketContract.BUCKET)
                        ? bucketObj.getString(AggregatableBucketContract.BUCKET)
                        : null;
        Filter filter = new Filter(flags);

        mFilterSet =
                !bucketObj.isNull(Filter.FilterContract.FILTERS)
                        ? filter.deserializeFilterSet(
                                bucketObj.getJSONArray(Filter.FilterContract.FILTERS))
                        : null;

        mNotFilterSet =
                !bucketObj.isNull(Filter.FilterContract.NOT_FILTERS)
                        ? filter.deserializeFilterSet(
                                bucketObj.getJSONArray(Filter.FilterContract.NOT_FILTERS))
                        : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregatableBucket)) {
            return false;
        }
        AggregatableBucket bucketObj = (AggregatableBucket) obj;
        return Objects.equals(mBucket, bucketObj.mBucket)
                && Objects.equals(mFilterSet, bucketObj.mFilterSet)
                && Objects.equals(mNotFilterSet, bucketObj.mNotFilterSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBucket, mFilterSet, mNotFilterSet);
    }

    /** Bucket id to match with source. */
    @Nullable
    public String getBucket() {
        return mBucket;
    }

    /** Returns AggregatableBucket filters. */
    @Nullable
    public List<FilterMap> getFilterSet() {
        return mFilterSet;
    }

    /** Returns AggregatableBucket not_filters, the reverse of filter. */
    @Nullable
    public List<FilterMap> getNotFilterSet() {
        return mNotFilterSet;
    }

    /** Aggregatable Bucket field keys. */
    public interface AggregatableBucketContract {
        String AGGREGATABLE_BUCKETS = "aggregatable_buckets";
        String BUCKET = "bucket";
    }
}
