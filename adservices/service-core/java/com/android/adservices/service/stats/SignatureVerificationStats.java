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

package com.android.adservices.service.stats;

import android.adservices.adselection.SignedContextualAds;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;

/** Data class for {@link SignedContextualAds} signature authentication stats */
@AutoValue
public abstract class SignatureVerificationStats {
    public static final int UNSET = 0;
    public static final String EMPTY_STRING = "";

    /** Returns the latency of fetching the key from key management store */
    public abstract long getKeyFetchLatency();

    /** Returns the latency of the serialization of a given {@link SignedContextualAds} object */
    public abstract long getSerializationLatency();

    /**
     * Returns the latency of the verifying the signature (only the signature verification, without
     * including the serialization and key fetch)
     */
    public abstract long getVerificationLatency();

    /** Returns number of keys fetched for a given ad tech */
    public abstract int getNumOfKeysFetched();

    /**
     * Returns the end status of the verification request
     *
     * <p>A verification request can end in success or a failure with various reasons
     */
    public abstract VerificationStatus getSignatureVerificationStatus();

    /** Returns enrollment id of the buyer that has a signature verification failure */
    @Nullable
    public abstract String getFailedSignatureBuyerEnrollmentId();

    /** Returns enrollment id of the seller that has a signature verification failure */
    @Nullable
    public abstract String getFailedSignatureSellerEnrollmentId();

    /** Returns caller package name that has a signature verification failure */
    @Nullable
    public abstract String getFailedSignatureCallerPackageName();

    /** Returns number of unknown error */
    public abstract int getFailureDetailUnknownError();

    /** Returns number of enrollment data not found for the buyer or was null. */
    public abstract int getFailureDetailNoEnrollmentDataForBuyer();

    /** Returns zero keys fetched for the buyer */
    public abstract int getFailureDetailNoKeysFetchedForBuyer();

    /** Returns signature is not formatted correctly */
    public abstract int getFailureDetailWrongSignatureFormat();

    /** Returns number of keys that has the wrong format */
    public abstract int getFailureDetailCountOfKeysWithWrongFormat();

    /** Returns number of keys that failed to verify the signature */
    public abstract int getFailureDetailCountOfKeysFailedToVerifySignature();

    public enum VerificationStatus {
        /** Failure reason is unknown */
        UNKNOWN(0),

        /** Signature is verified successfully */
        VERIFIED(1),

        /** Verification is failed against all the keys fetched from the key store. */
        VERIFICATION_FAILED(2);

        private final int mValue;

        VerificationStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** Returns a builder for this instance */
    public abstract Builder toBuilder();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_SignatureVerificationStats.Builder()
                .setKeyFetchLatency(UNSET)
                .setSerializationLatency(UNSET)
                .setVerificationLatency(UNSET)
                .setNumOfKeysFetched(UNSET)
                .setSignatureVerificationStatus(VerificationStatus.UNKNOWN)
                .setFailedSignatureSellerEnrollmentId(EMPTY_STRING)
                .setFailedSignatureBuyerEnrollmentId(EMPTY_STRING)
                .setFailedSignatureCallerPackageName(EMPTY_STRING)
                .setFailureDetailUnknownError(UNSET)
                .setFailureDetailNoEnrollmentDataForBuyer(UNSET)
                .setFailureDetailNoKeysFetchedForBuyer(UNSET)
                .setFailureDetailWrongSignatureFormat(UNSET)
                .setFailureDetailCountOfKeysWithWrongFormat(UNSET)
                .setFailureDetailCountOfKeysFailedToVerifySignature(UNSET);
    }

    /** Builder class for SignatureVerificationStats */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the latency of fetching the key from key management store */
        @NonNull
        public abstract Builder setKeyFetchLatency(long keyFetchLatency);

        /** Sets the latency of the serialization of a given {@link SignedContextualAds} object */
        @NonNull
        public abstract Builder setSerializationLatency(long serializationLatency);

        /**
         * Sets the latency of the verifying the signature (only the signature verification, without
         * including the serialization and key fetch)
         */
        @NonNull
        public abstract Builder setVerificationLatency(long verificationLatency);

        /** Sets number of keys fetched for a given ad tech */
        @NonNull
        public abstract Builder setNumOfKeysFetched(int value);

        /**
         * Sets the end status of the verification request
         *
         * <p>A verification request can end in success or a failure with various reasons
         */
        @NonNull
        public abstract Builder setSignatureVerificationStatus(
                VerificationStatus verificationStatus);

        /** Sets enrollment id of the buyer that has a signature verification failure */
        @NonNull
        public abstract Builder setFailedSignatureBuyerEnrollmentId(String value);

        /** Sets enrollment id of the seller that has a signature verification failure */
        @NonNull
        public abstract Builder setFailedSignatureSellerEnrollmentId(String value);

        /** Sets caller package name that has a signature verification failure */
        @NonNull
        public abstract Builder setFailedSignatureCallerPackageName(String value);

        /** Sets number of unknown error */
        @NonNull
        public abstract Builder setFailureDetailUnknownError(int value);

        /** Sets number of enrollment data not found for the buyer or was null. */
        @NonNull
        public abstract Builder setFailureDetailNoEnrollmentDataForBuyer(int value);

        /** Sets zero keys fetched for the buyer */
        @NonNull
        public abstract Builder setFailureDetailNoKeysFetchedForBuyer(int value);

        /** Sets signature is not formatted correctly */
        @NonNull
        public abstract Builder setFailureDetailWrongSignatureFormat(int value);

        /** Sets number of keys that has the wrong format */
        @NonNull
        public abstract Builder setFailureDetailCountOfKeysWithWrongFormat(int value);

        /** Sets number of keys that failed to verify the signature */
        @NonNull
        public abstract Builder setFailureDetailCountOfKeysFailedToVerifySignature(int value);

        /** Build the {@link SignatureVerificationStats} */
        @NonNull
        public abstract SignatureVerificationStats build();
    }
}
