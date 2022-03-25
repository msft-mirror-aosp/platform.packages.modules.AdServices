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

package android.adservices.topics;

import static android.adservices.topics.TopicsManager.RESULT_OK;

import android.adservices.topics.TopicsManager.ResultCode;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represent the result from the getTopics API.
 *
 * @hide
 */
public final class GetTopicsResult implements Parcelable {
    private final @ResultCode int mResultCode;
    @Nullable private final String mErrorMessage;
    private final List<Long> mTaxonomyVersions;
    private final List<Long> mModelVersions;
    private final List<String> mTopics;

    private GetTopicsResult(
            @ResultCode int resultCode,
            @Nullable String errorMessage,
            @NonNull List<Long> taxonomyVersions,
            @NonNull List<Long> modelVersions,
            @NonNull List<String> topics) {
        mResultCode = resultCode;
        mErrorMessage = errorMessage;
        mTaxonomyVersions = taxonomyVersions;
        mModelVersions = modelVersions;
        mTopics = topics;
    }

    private GetTopicsResult(@NonNull Parcel in) {
        mResultCode = in.readInt();
        mErrorMessage = in.readString();

        mTaxonomyVersions = Collections.unmodifiableList(readLongList(in));
        mModelVersions = Collections.unmodifiableList(readLongList(in));

        List<String> topicsMutable = new ArrayList<>();
        in.readStringList(topicsMutable);
        mTopics = Collections.unmodifiableList(topicsMutable);
    }

    public static final @NonNull Creator<GetTopicsResult> CREATOR =
            new Parcelable.Creator<GetTopicsResult>() {
                @Override
                public GetTopicsResult createFromParcel(Parcel in) {
                    return new GetTopicsResult(in);
                }

                @Override
                public GetTopicsResult[] newArray(int size) {
                    return new GetTopicsResult[size];
                }
            };

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mResultCode);
        out.writeString(mErrorMessage);
        writeLongList(out, mTaxonomyVersions);
        writeLongList(out, mModelVersions);
        out.writeStringList(mTopics);
    }

    /** Returns {@code true} if {@link #getResultCode} equals {@link GetTopicsResult#RESULT_OK}. */
    public boolean isSuccess() {
        return getResultCode() == RESULT_OK;
    }

    /** Returns one of the {@code RESULT} constants defined in {@link GetTopicsResult}. */
    public @ResultCode int getResultCode() {
        return mResultCode;
    }

    /**
     * Returns the error message associated with this result.
     *
     * <p>If {@link #isSuccess} is {@code true}, the error message is always {@code null}. The error
     * message may be {@code null} even if {@link #isSuccess} is {@code false}.
     */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /** Get the Taxonomy Versions. */
    public List<Long> getTaxonomyVersions() {
        return mTaxonomyVersions;
    }

    /** Get the Model Versions. */
    public List<Long> getModelVersions() {
        return mModelVersions;
    }

    @NonNull
    public List<String> getTopics() {
        return mTopics;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof GetTopicsResult)) {
            return false;
        }

        GetTopicsResult that = (GetTopicsResult) o;

        return mResultCode == that.mResultCode
                && Objects.equals(mErrorMessage, that.mErrorMessage)
                && mTaxonomyVersions.equals(that.mTaxonomyVersions)
                && mModelVersions.equals(that.mModelVersions)
                && mTopics.equals(that.mTopics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mResultCode, mErrorMessage, mTaxonomyVersions, mModelVersions, mTopics);
    }

    // Read the list of long from parcel.
    private static List<Long> readLongList(@NonNull Parcel in) {
        List<Long> list = new ArrayList<>();

        int toReadCount = in.readInt();
        // Negative toReadCount is handled implicitly
        for (int i = 0; i < toReadCount; i++) {
            list.add(in.readLong());
        }

        return list;
    }

    // Write a List of Long to parcel.
    private static void writeLongList(@NonNull Parcel out, @Nullable List<Long> val) {
        if (val == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(val.size());
        for (Long l : val) {
            out.writeLong(l);
        }
    }

    /**
     * Builder for {@link GetTopicsResult} objects.
     *
     * @hide
     */
    public static final class Builder {
        private @ResultCode int mResultCode;
        @Nullable private String mErrorMessage;
        private List<Long> mTaxonomyVersions = new ArrayList<>();
        private List<Long> mModelVersions = new ArrayList<>();
        private List<String> mTopics = new ArrayList<>();

        public Builder() {}

        /** Set the Result Code. */
        public @NonNull Builder setResultCode(@ResultCode int resultCode) {
            mResultCode = resultCode;
            return this;
        }

        /** Set the Error Message. */
        public @NonNull Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /** Set the Taxonomy Version. */
        public @NonNull Builder setTaxonomyVersions(@NonNull List<Long> taxonomyVersions) {
            mTaxonomyVersions = taxonomyVersions;
            return this;
        }

        /** Set the Model Version. */
        public @NonNull Builder setModelVersions(@NonNull List<Long> modelVersions) {
            mModelVersions = modelVersions;
            return this;
        }

        /** Set the list of the returned Topics */
        public @NonNull Builder setTopics(@NonNull List<String> topics) {
            mTopics = topics;
            return this;
        }

        /**
         * Builds a {@link GetTopicsResult} instance.
         *
         * <p>throws IllegalArgumentException if any of the params are null or there is any mismatch
         * in the size of ModelVersions and TaxonomyVersions.
         */
        public @NonNull GetTopicsResult build() {
            if (mTopics == null || mTaxonomyVersions == null || mModelVersions == null) {
                throw new IllegalArgumentException(
                        "Topics or TaxonomyVersion or ModelVersion is null");
            }

            if (mTopics.size() != mTaxonomyVersions.size()
                    || mTopics.size() != mModelVersions.size()) {
                throw new IllegalArgumentException("Size mismatch in Topics");
            }

            return new GetTopicsResult(
                    mResultCode, mErrorMessage, mTaxonomyVersions, mModelVersions, mTopics);
        }
    }
}
