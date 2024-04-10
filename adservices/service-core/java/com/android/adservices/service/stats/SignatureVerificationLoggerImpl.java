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

import static com.android.adservices.service.stats.SignatureVerificationStats.EMPTY_STRING;
import static com.android.adservices.service.stats.SignatureVerificationStats.UNSET;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Logs signature verification metric for a given {@link
 * android.adservices.adselection.SignedContextualAds} object per buyer. Collects data into {@link
 * SignatureVerificationStats} object.
 */
public class SignatureVerificationLoggerImpl implements SignatureVerificationLogger {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String INVALID_SIGNING_VERIFICATION_STATUS =
            "The signing verification status should be either 0 or 1.";

    @VisibleForTesting
    static final String REPEATED_START_SIGNATURE_VERIFICATION =
            "The logger has already set the start of the signature verification process.";

    @VisibleForTesting
    static final String MISSING_START_SIGNATURE_VERIFICATION =
            "The logger should set the start of the signature verification process.";

    @VisibleForTesting
    static final String REPEATED_END_SIGNATURE_VERIFICATION =
            "The logger has already set the end signature verification process";

    @VisibleForTesting
    static final String REPEATED_START_KEY_FETCH_FOR_SIGNATURE_VERIFICATION =
            "The logger has already set the start key fetch for signature verification process";

    @VisibleForTesting
    static final String MISSING_START_KEY_FETCH_FOR_SIGNATURE_VERIFICATION =
            "The logger should set the start of the key fetch for signature verification process.";

    @VisibleForTesting
    static final String REPEATED_END_KEY_FETCH_FOR_SIGNATURE_VERIFICATION =
            "The logger has already set the end key fetch for signature verification process";

    @VisibleForTesting
    static final String REPEATED_START_SERIALIZATION_FOR_SIGNATURE_VERIFICATION =
            "The logger has already set the start serialization for signature verification process";

    @VisibleForTesting
    static final String MISSING_START_SERIALIZATION_FOR_SIGNATURE_VERIFICATION =
            "The logger should set the start of serialization for signature verification process.";

    @VisibleForTesting
    static final String REPEATED_END_SERIALIZATION_FOR_SIGNATURE_VERIFICATION =
            "The logger has already set the end serialization for signature verification process";

    @VisibleForTesting
    static final String MISSING_TIMESTAMPS_FOR_SIGNING_LOGGING_LATENCY =
            "The timestamps are not set for signing verification process";

    @VisibleForTesting
    static final String ENROLLMENT_DETAILS_SHOULD_ONLY_LOGGED_IF_SIGNATURE_VERIFICATION_ERROR =
            "Enrollment details should only be logged in case of an signature verification error. "
                    + "Purging enrollment data...";

    private long mSignatureVerificationStartTimestamp;
    private long mSignatureVerificationEndTimestamp;
    private long mKeyFetchForSignatureVerificationStartTimestamp;
    private long mKeyFetchForSignatureVerificationEndTimestamp;
    private long mSerializationForSignatureVerificationStartTimestamp;
    private long mSerializationForSignatureVerificationEndTimestamp;
    private int mNumOfKeysFetched;
    private String mFailedSignatureBuyerEnrollmentId;
    private String mFailedSignatureSellerEnrollmentId;
    private String mFailedSignatureCallerPackageName;
    private int mFailureDetailUnknownError;
    private int mFailureDetailNoEnrollmentDataForBuyer;
    private int mFailureDetailNoKeysFetchedForBuyer;
    private int mFailureDetailWrongSignatureFormat;
    private int mFailureDetailCountOfKeysWithWrongFormat;
    private int mFailureDetailCountOfKeysFailedToVerifySignature;

    @NonNull private final Clock mClock;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    public SignatureVerificationLoggerImpl(@NonNull AdServicesLogger adServicesLogger) {
        this(adServicesLogger, Clock.getInstance());
    }

    @VisibleForTesting
    public SignatureVerificationLoggerImpl(
            @NonNull AdServicesLogger adServicesLogger, @NonNull Clock clock) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);

        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        sLogger.v("SignatureVerificationLogger starts.");
    }

    /** Records the start of a signature verification stage */
    public void startSignatureVerification() {
        if (mSignatureVerificationStartTimestamp > UNSET) {
            sLogger.w(REPEATED_START_SIGNATURE_VERIFICATION);
            return;
        }
        mSignatureVerificationStartTimestamp = getServiceElapsedTimestamp();
    }

    /** Records the end of a signature verification stage */
    public void endSignatureVerification() {
        if (mSignatureVerificationStartTimestamp == UNSET) {
            sLogger.w(MISSING_START_SIGNATURE_VERIFICATION);
            return;
        }
        if (mSignatureVerificationEndTimestamp > UNSET) {
            sLogger.w(REPEATED_END_SIGNATURE_VERIFICATION);
            return;
        }
        mSignatureVerificationEndTimestamp = getServiceElapsedTimestamp();
    }

    /** Records the start of a key fetch stage */
    public void startKeyFetchForSignatureVerification() {
        if (mKeyFetchForSignatureVerificationStartTimestamp > UNSET) {
            sLogger.w(REPEATED_START_KEY_FETCH_FOR_SIGNATURE_VERIFICATION);
            return;
        }
        mKeyFetchForSignatureVerificationStartTimestamp = getServiceElapsedTimestamp();
    }

    /** Records the end of a key fetch stage */
    public void endKeyFetchForSignatureVerification() {
        if (mKeyFetchForSignatureVerificationStartTimestamp == UNSET) {
            sLogger.w(MISSING_START_KEY_FETCH_FOR_SIGNATURE_VERIFICATION);
            return;
        }
        if (mKeyFetchForSignatureVerificationEndTimestamp > UNSET) {
            sLogger.w(REPEATED_END_KEY_FETCH_FOR_SIGNATURE_VERIFICATION);
            return;
        }
        mKeyFetchForSignatureVerificationEndTimestamp = getServiceElapsedTimestamp();
    }

    /** Records the start of a serialization stage */
    public void startSerializationForSignatureVerification() {
        if (mSerializationForSignatureVerificationStartTimestamp > UNSET) {
            sLogger.w(REPEATED_START_SERIALIZATION_FOR_SIGNATURE_VERIFICATION);
            return;
        }
        mSerializationForSignatureVerificationStartTimestamp = getServiceElapsedTimestamp();
    }

    /** Records the end of a serialization stage */
    public void endSerializationForSignatureVerification() {
        if (mSerializationForSignatureVerificationStartTimestamp == UNSET) {
            sLogger.w(MISSING_START_SERIALIZATION_FOR_SIGNATURE_VERIFICATION);
            return;
        }
        if (mSerializationForSignatureVerificationEndTimestamp > UNSET) {
            sLogger.w(REPEATED_END_SERIALIZATION_FOR_SIGNATURE_VERIFICATION);
            return;
        }
        mSerializationForSignatureVerificationEndTimestamp = getServiceElapsedTimestamp();
    }

    /** Records the number of keys fetched from key store */
    public void setNumOfKeysFetched(int numOfKeysFetched) {
        mNumOfKeysFetched = numOfKeysFetched;
    }

    /** Records the buyer enrollment id in case of a failed signature verification */
    public void setFailedSignatureBuyerEnrollmentId(String enrollmentId) {
        mFailedSignatureBuyerEnrollmentId = enrollmentId;
    }

    /** Records the seller enrollment id in case of a failed signature verification */
    public void setFailedSignatureSellerEnrollmentId(String enrollmentId) {
        mFailedSignatureSellerEnrollmentId = enrollmentId;
    }

    /** Records the caller package id enrollment id in case of a failed signature verification */
    public void setFailedSignatureCallerPackageName(String callerPackageName) {
        mFailedSignatureCallerPackageName = callerPackageName;
    }

    /** Records an unknown error caused failure for signature verification */
    public void setFailureDetailUnknownError() {
        mFailureDetailUnknownError = 1;
    }

    /** Records a missing enrollment data caused failure for signature verification */
    public void setFailureDetailNoEnrollmentDataForBuyer() {
        mFailureDetailNoEnrollmentDataForBuyer = 1;
    }

    /** Records no keys fetched for buyer caused failure for signature verification */
    public void setFailureDetailNoKeysFetchedForBuyer() {
        mFailureDetailNoKeysFetchedForBuyer = 1;
    }

    /** Records buyer using wrong signature format caused failure for signature verification */
    public void setFailureDetailWrongSignatureFormat() {
        mFailureDetailWrongSignatureFormat = 1;
    }

    /** Records number of keys with wrong key format */
    public void addFailureDetailCountOfKeysWithWrongFormat() {
        mFailureDetailCountOfKeysWithWrongFormat++;
    }

    /** Records number of keys that failed to verify signature */
    public void addFailureDetailCountOfKeysFailedToVerifySignature() {
        mFailureDetailCountOfKeysFailedToVerifySignature++;
    }

    /**
     * This method should be called at the end of signing verification for each {@link
     * android.adservices.adselection.SignedContextualAds} object.
     */
    public void close(int signingVerificationStatus) {
        try {
            sLogger.v("Log the SignatureVerificationStats to the AdServicesLog.");

            SignatureVerificationStats.VerificationStatus verificationStatus;
            if (signingVerificationStatus
                    == SignatureVerificationStats.VerificationStatus.VERIFIED.getValue()) {
                sLogger.v(
                        "Log SignatureVerificationStats for a successful signature verification"
                                + " run.");
                verificationStatus = SignatureVerificationStats.VerificationStatus.VERIFIED;
            } else if (signingVerificationStatus
                    == SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED
                            .getValue()) {
                sLogger.v(
                        "Log SignatureVerificationStats for a failed signature verification run.");
                verificationStatus =
                        SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED;
            } else {
                sLogger.w(INVALID_SIGNING_VERIFICATION_STATUS);
                flush();
                return;
            }

            boolean isStatusVerified =
                    verificationStatus == SignatureVerificationStats.VerificationStatus.VERIFIED;
            boolean anyTimestampMissing =
                    mKeyFetchForSignatureVerificationEndTimestamp == UNSET
                            || mSerializationForSignatureVerificationEndTimestamp == UNSET
                            || mSignatureVerificationEndTimestamp == UNSET;
            if (isStatusVerified && anyTimestampMissing) {
                sLogger.v(MISSING_TIMESTAMPS_FOR_SIGNING_LOGGING_LATENCY);
                flush();
                return;
            }

            SignatureVerificationStats signatureVerificationStats =
                    getSignatureVerificationStats(verificationStatus);

            if (isStatusVerified && isAnyEnrollmentDataSet(signatureVerificationStats)) {
                sLogger.e(ENROLLMENT_DETAILS_SHOULD_ONLY_LOGGED_IF_SIGNATURE_VERIFICATION_ERROR);
                signatureVerificationStats = purgeEnrollmentIdData(signatureVerificationStats);
            }

            sLogger.v("Logging Signing Verification atom: %s", signatureVerificationStats);
            mAdServicesLogger.logSignatureVerificationStats(signatureVerificationStats);
            flush();
        } catch (Exception e) {
            sLogger.e(
                    e,
                    "Unknown error occurred during signature verification logging. Flushing the"
                            + " logs");
            flush();
        }
    }

    private static boolean isAnyEnrollmentDataSet(
            SignatureVerificationStats signatureVerificationStats) {
        String sellerEnrollmentId =
                signatureVerificationStats.getFailedSignatureSellerEnrollmentId();
        String buyerEnrollmentId = signatureVerificationStats.getFailedSignatureBuyerEnrollmentId();
        String callerPackageName = signatureVerificationStats.getFailedSignatureCallerPackageName();
        boolean isSellerEnrollmentIdSet =
                Objects.nonNull(sellerEnrollmentId) && !sellerEnrollmentId.equals(EMPTY_STRING);
        boolean isBuyerEnrollmentIdSet =
                Objects.nonNull(buyerEnrollmentId) && !buyerEnrollmentId.equals(EMPTY_STRING);
        boolean isCallerPackageNameSet =
                Objects.nonNull(callerPackageName) && !callerPackageName.equals(EMPTY_STRING);
        return isSellerEnrollmentIdSet || isBuyerEnrollmentIdSet || isCallerPackageNameSet;
    }

    private SignatureVerificationStats getSignatureVerificationStats(
            SignatureVerificationStats.VerificationStatus verificationStatus) {
        return SignatureVerificationStats.builder()
                .setKeyFetchLatency(
                        mKeyFetchForSignatureVerificationEndTimestamp
                                - mKeyFetchForSignatureVerificationStartTimestamp)
                .setSerializationLatency(
                        mSerializationForSignatureVerificationEndTimestamp
                                - mSerializationForSignatureVerificationStartTimestamp)
                .setVerificationLatency(
                        mSignatureVerificationEndTimestamp - mSignatureVerificationStartTimestamp)
                .setNumOfKeysFetched(mNumOfKeysFetched)
                .setSignatureVerificationStatus(verificationStatus)
                .setFailedSignatureSellerEnrollmentId(mFailedSignatureSellerEnrollmentId)
                .setFailedSignatureBuyerEnrollmentId(mFailedSignatureBuyerEnrollmentId)
                .setFailedSignatureCallerPackageName(mFailedSignatureCallerPackageName)
                .setFailureDetailUnknownError(mFailureDetailUnknownError)
                .setFailureDetailNoEnrollmentDataForBuyer(mFailureDetailNoEnrollmentDataForBuyer)
                .setFailureDetailNoKeysFetchedForBuyer(mFailureDetailNoKeysFetchedForBuyer)
                .setFailureDetailWrongSignatureFormat(mFailureDetailWrongSignatureFormat)
                .setFailureDetailCountOfKeysWithWrongFormat(
                        mFailureDetailCountOfKeysWithWrongFormat)
                .setFailureDetailCountOfKeysFailedToVerifySignature(
                        mFailureDetailCountOfKeysFailedToVerifySignature)
                .build();
    }

    private void flush() {
        mKeyFetchForSignatureVerificationEndTimestamp = UNSET;
        mKeyFetchForSignatureVerificationStartTimestamp = UNSET;
        mSerializationForSignatureVerificationEndTimestamp = UNSET;
        mSerializationForSignatureVerificationStartTimestamp = UNSET;
        mNumOfKeysFetched = UNSET;
        mSignatureVerificationEndTimestamp = UNSET;
        mSignatureVerificationStartTimestamp = UNSET;
        mFailedSignatureSellerEnrollmentId = EMPTY_STRING;
        mFailedSignatureBuyerEnrollmentId = EMPTY_STRING;
        mFailedSignatureCallerPackageName = EMPTY_STRING;
        mFailureDetailUnknownError = UNSET;
        mFailureDetailNoEnrollmentDataForBuyer = UNSET;
        mFailureDetailNoKeysFetchedForBuyer = UNSET;
        mFailureDetailWrongSignatureFormat = UNSET;
        mFailureDetailCountOfKeysWithWrongFormat = UNSET;
        mFailureDetailCountOfKeysFailedToVerifySignature = UNSET;
    }

    private SignatureVerificationStats purgeEnrollmentIdData(
            SignatureVerificationStats signatureVerificationStats) {
        return signatureVerificationStats.toBuilder()
                .setFailedSignatureBuyerEnrollmentId(EMPTY_STRING)
                .setFailedSignatureSellerEnrollmentId(EMPTY_STRING)
                .setFailedSignatureCallerPackageName(EMPTY_STRING)
                .build();
    }

    private long getServiceElapsedTimestamp() {
        return mClock.elapsedRealtime();
    }
}
