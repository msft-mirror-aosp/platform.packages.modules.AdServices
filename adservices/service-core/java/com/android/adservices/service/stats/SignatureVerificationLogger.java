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

public interface SignatureVerificationLogger {
    /** Records the start of a signature verification stage */
    void startSignatureVerification();

    /** Records the end of a signature verification stage */
    void endSignatureVerification();

    /** Records the start of a key fetch stage */
    void startKeyFetchForSignatureVerification();

    /** Records the end of a key fetch stage */
    void endKeyFetchForSignatureVerification();

    /** Records the start of a serialization stage */
    void startSerializationForSignatureVerification();

    /** Records the end of a serialization stage */
    void endSerializationForSignatureVerification();

    /** Records the number of keys fetched from key store */
    void setNumOfKeysFetched(int numOfKeysFetched);

    /** Records the buyer enrollment id in case of a failed signature verification */
    void setFailedSignatureBuyerEnrollmentId(String enrollmentId);

    /** Records the seller enrollment id in case of a failed signature verification */
    void setFailedSignatureSellerEnrollmentId(String enrollmentId);

    /** Records the caller package id enrollment id in case of a failed signature verification */
    void setFailedSignatureCallerPackageName(String callerPackageName);

    /** Records an unknown error caused failure for signature verification */
    void setFailureDetailUnknownError();

    /** Records a missing enrollment data caused failure for signature verification */
    void setFailureDetailNoEnrollmentDataForBuyer();

    /** Records no keys fetched for buyer caused failure for signature verification */
    void setFailureDetailNoKeysFetchedForBuyer();

    /** Records buyer using wrong signature format caused failure for signature verification */
    void setFailureDetailWrongSignatureFormat();

    /** Records number of keys with wrong key format */
    void addFailureDetailCountOfKeysWithWrongFormat();

    /** Records number of keys that failed to verify signature */
    void addFailureDetailCountOfKeysFailedToVerifySignature();

    /**
     * This method should be called at the end of signing verification for each {@link
     * android.adservices.adselection.SignedContextualAds} object.
     */
    void close(int signingVerificationStatus);
}
