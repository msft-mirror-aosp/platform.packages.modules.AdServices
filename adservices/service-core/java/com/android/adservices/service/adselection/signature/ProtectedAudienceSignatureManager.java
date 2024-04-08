/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.adselection.signature;

import android.adservices.adselection.SignedContextualAds;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.SignatureVerificationLogger;
import com.android.adservices.service.stats.SignatureVerificationStats;

import com.google.common.annotations.VisibleForTesting;

import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manages signature verification for Protected Audience Contextual Ads
 *
 * <p>See {@link android.adservices.adselection.SignedContextualAds} for more details.
 */
public class ProtectedAudienceSignatureManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @VisibleForTesting public static final String EMPTY_STRING_FOR_MISSING_ENROLLMENT_ID = "";

    /**
     * This P-256 ECDSA key is used to verify signatures if {@link
     * com.android.adservices.service.FlagsConstants #KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK} is set to
     * true.
     *
     * <p>This enables CTS and integration testing.
     *
     * <p>To test with this key, {@link SignedContextualAds} should be signed with {@link
     * ProtectedAudienceSignatureManager#PRIVATE_TEST_KEY_STRING}.
     */
    public static final String PUBLIC_TEST_KEY_STRING =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+Eyo0TOllW8as2pTTzxawQ57pXJiH16VERgHqcV1/YpADt3iq6"
                    + "9vbhwW8Ksi3M0GrxacOuge/AwiM7Uh6+V3PA==";

    /**
     * Private key pair of the {@link ProtectedAudienceSignatureManager#PUBLIC_TEST_KEY_STRING}
     *
     * <p>See {@link ProtectedAudienceSignatureManager#PUBLIC_TEST_KEY_STRING}
     */
    public static final String PRIVATE_TEST_KEY_STRING =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgECetqRr9eE9DKKjILR+hP66Y1niEw/bqPD/MNx"
                    + "PTMvmhRANCAAT4TKjRM6WVbxqzalNPPFrBDnulcmIfXpURGAepxXX9ikAO3eKrr29uHBbwqyLczQ"
                    + "avFpw66B78DCIztSHr5Xc8";

    @NonNull private final EnrollmentDao mEnrollmentDao;
    @NonNull private final EncryptionKeyDao mEncryptionKeyDao;
    @NonNull private final SignatureVerificationLogger mSignatureVerificationLogger;
    private final boolean mIsEnrollmentCheckEnabled;
    private final SignatureVerifier mSignatureVerifier;

    public ProtectedAudienceSignatureManager(
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull SignatureVerificationLogger signatureVerificationLogger,
            boolean isEnrollmentCheckEnabled) {
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(encryptionKeyDao);
        Objects.requireNonNull(signatureVerificationLogger);

        mEnrollmentDao = enrollmentDao;
        mEncryptionKeyDao = encryptionKeyDao;
        mSignatureVerificationLogger = signatureVerificationLogger;
        mIsEnrollmentCheckEnabled = isEnrollmentCheckEnabled;

        mSignatureVerifier = new ECDSASignatureVerifier(mSignatureVerificationLogger);
    }

    /**
     * Returns whether is the given {@link SignedContextualAds} object is valid or not
     *
     * @param buyer Ad tech's identifier to resolve their public key
     * @param signedContextualAds contextual ads object to verify
     * @return true if the object is valid else false
     */
    public boolean isVerified(
            @NonNull AdTechIdentifier buyer,
            @NonNull AdTechIdentifier seller,
            @NonNull String callerPackageName,
            @NonNull SignedContextualAds signedContextualAds) {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(seller);
        Objects.requireNonNull(callerPackageName);
        Objects.requireNonNull(signedContextualAds);

        try {
            List<byte[]> publicKeys = fetchPublicKeyForAdTech(buyer);
            sLogger.v("Received %s keys", publicKeys.size());

            byte[] serialized = serializeSignedContextualAds(signedContextualAds);
            sLogger.v("Serialized contextual ads object");

            logStartSignatureVerification();
            for (byte[] publicKey : publicKeys) {
                if (mSignatureVerifier.verify(
                        publicKey, serialized, signedContextualAds.getSignature())) {
                    sLogger.v(
                            "Signature is verified with key: '%s'.",
                            Base64.getEncoder().encodeToString(publicKey));
                    endSuccessfulSigningProcess();
                    return true;
                } else {
                    logKeyFailedSignatureVerification();
                }
                sLogger.v(
                        "Key '%s' didn't verify the signature. Trying the next key...",
                        Base64.getEncoder().encodeToString(publicKey));
            }
            sLogger.v("All keys are exhausted and signature is not verified!");
            endFailedSigningProcess(buyer, seller, callerPackageName);
            return false;
        } catch (Exception e) {
            sLogger.v("Unknown error during signature verification: %s", e);
            boolean isUnknownError = true;
            endFailedSigningProcess(buyer, seller, callerPackageName, isUnknownError);
            return false;
        }
    }

    @VisibleForTesting
    List<byte[]> fetchPublicKeyForAdTech(AdTechIdentifier adTech) {
        logStartKeyFetch();
        Base64.Decoder decoder = Base64.getDecoder();
        if (!mIsEnrollmentCheckEnabled) {
            sLogger.v("Enrollment check is disabled, returning the default key");
            List<byte[]> toReturn =
                    Collections.singletonList(decoder.decode(PUBLIC_TEST_KEY_STRING));
            logSuccessfulKeyFetch(toReturn.size());
            return toReturn;
        }

        try {
            sLogger.v("Fetching EnrollmentData for %s", adTech);
            EnrollmentData enrollmentData =
                    mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTech);

            if (enrollmentData == null || enrollmentData.getEnrollmentId() == null) {
                sLogger.v("Enrollment data or id is not found for ad tech: %s", adTech);
                logNoEnrollmentDataForBuyer();
                logFailedKeyFetch();
                return Collections.emptyList();
            }

            sLogger.v("Fetching signature keys for %s", enrollmentData.getEnrollmentId());
            List<EncryptionKey> encryptionKeys =
                    mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                            enrollmentData.getEnrollmentId(), EncryptionKey.KeyType.SIGNING);

            sLogger.v("Received %s signing key(s)", encryptionKeys.size());
            List<byte[]> publicKeys =
                    encryptionKeys.stream()
                            .sorted(Comparator.comparingLong(EncryptionKey::getExpiration))
                            .map(key -> decoder.decode(key.getBody()))
                            .collect(Collectors.toList());
            logSuccessfulKeyFetch(publicKeys.size());
            return publicKeys;
        } catch (Exception e) {
            logFailedKeyFetch();
            sLogger.e(e, "Unknown error during key fetch for signature verification");
            throw e;
        }
    }

    private byte[] serializeSignedContextualAds(SignedContextualAds signedContextualAds) {
        logStartSerialization();
        byte[] toReturn = new SignedContextualAdsHashUtil().serialize(signedContextualAds);
        logEndSerialization();
        return toReturn;
    }

    private void logStartKeyFetch() {
        mSignatureVerificationLogger.startKeyFetchForSignatureVerification();
    }

    private void logFailedKeyFetch() {
        mSignatureVerificationLogger.endKeyFetchForSignatureVerification();
        mSignatureVerificationLogger.setNumOfKeysFetched(0);
    }

    private void logStartSerialization() {
        mSignatureVerificationLogger.startSerializationForSignatureVerification();
    }

    private void logEndSerialization() {
        mSignatureVerificationLogger.endSerializationForSignatureVerification();
    }

    private void logSuccessfulKeyFetch(int numOfKeysFetched) {
        mSignatureVerificationLogger.endKeyFetchForSignatureVerification();
        mSignatureVerificationLogger.setNumOfKeysFetched(numOfKeysFetched);
        if (numOfKeysFetched == 0) {
            mSignatureVerificationLogger.setFailureDetailNoKeysFetchedForBuyer();
        }
    }

    private void logStartSignatureVerification() {
        mSignatureVerificationLogger.startSignatureVerification();
    }

    private void logKeyFailedSignatureVerification() {
        mSignatureVerificationLogger.addFailureDetailCountOfKeysFailedToVerifySignature();
    }

    private void logNoEnrollmentDataForBuyer() {
        mSignatureVerificationLogger.setFailureDetailNoEnrollmentDataForBuyer();
    }

    private void endFailedSigningProcess(
            AdTechIdentifier buyer, AdTechIdentifier seller, String callerPackageName) {
        boolean isKnownError = false;
        endFailedSigningProcess(buyer, seller, callerPackageName, isKnownError);
    }

    private void endFailedSigningProcess(
            AdTechIdentifier buyer,
            AdTechIdentifier seller,
            String callerPackageName,
            boolean isUnknownError) {
        mSignatureVerificationLogger.endSignatureVerification();
        mSignatureVerificationLogger.setFailedSignatureBuyerEnrollmentId(
                resolveEnrollmentIdFromAdTechIdentifier(buyer));
        mSignatureVerificationLogger.setFailedSignatureSellerEnrollmentId(
                resolveEnrollmentIdFromAdTechIdentifier(seller));
        mSignatureVerificationLogger.setFailedSignatureCallerPackageName(callerPackageName);
        if (isUnknownError) {
            mSignatureVerificationLogger.setFailureDetailUnknownError();
        }
        mSignatureVerificationLogger.close(
                SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED.getValue());
    }

    private void endSuccessfulSigningProcess() {
        mSignatureVerificationLogger.endSignatureVerification();
        mSignatureVerificationLogger.close(
                SignatureVerificationStats.VerificationStatus.VERIFIED.getValue());
    }

    private String resolveEnrollmentIdFromAdTechIdentifier(AdTechIdentifier adTechIdentifier) {
        EnrollmentData enrollmentData =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTechIdentifier);
        if (Objects.isNull(enrollmentData) || Objects.isNull(enrollmentData.getEnrollmentId())) {
            return EMPTY_STRING_FOR_MISSING_ENROLLMENT_ID;
        }
        return enrollmentData.getEnrollmentId();
    }
}
