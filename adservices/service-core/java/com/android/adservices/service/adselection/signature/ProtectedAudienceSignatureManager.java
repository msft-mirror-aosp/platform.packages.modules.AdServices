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
    private final EnrollmentDao mEnrollmentDao;
    private final EncryptionKeyDao mEncryptionKeyDao;

    private final SignatureVerifier mSignatureVerifier;

    public ProtectedAudienceSignatureManager(
            @NonNull EnrollmentDao enrollmentDao, @NonNull EncryptionKeyDao encryptionKeyDao) {
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(encryptionKeyDao);

        mEnrollmentDao = enrollmentDao;
        mEncryptionKeyDao = encryptionKeyDao;

        mSignatureVerifier = new ECDSASignatureVerifier();
    }

    @VisibleForTesting
    ProtectedAudienceSignatureManager(
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull EncryptionKeyDao encryptionKeyDao,
            @NonNull SignatureVerifier signatureVerifier) {
        mEnrollmentDao = enrollmentDao;
        mEncryptionKeyDao = encryptionKeyDao;
        mSignatureVerifier = signatureVerifier;
    }

    /**
     * Returns whether is the given {@link SignedContextualAds} object is valid or not
     *
     * @param buyer Ad tech's identifier to resolve their public key
     * @param signedContextualAds contextual ads object to verify
     * @return true if the object is valid else false
     */
    public boolean isVerified(
            @NonNull AdTechIdentifier buyer, @NonNull SignedContextualAds signedContextualAds) {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(signedContextualAds);

        List<byte[]> publicKeys = fetchPublicKeyForAdTech(buyer);
        boolean isVerified = false;
        SignedContextualAdsHashUtil contextualAdsHashUtil;
        for (byte[] publicKey : publicKeys) {
            contextualAdsHashUtil = new SignedContextualAdsHashUtil();
            byte[] serialized = contextualAdsHashUtil.serialize(signedContextualAds);
            isVerified =
                    mSignatureVerifier.verify(
                            publicKey, serialized, signedContextualAds.getSignature());
        }
        return isVerified;
    }

    @VisibleForTesting
    List<byte[]> fetchPublicKeyForAdTech(AdTechIdentifier adTech) {
        sLogger.v("Fetching EnrollmentData for %s", adTech);
        EnrollmentData enrollmentData =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adTech);

        if (enrollmentData == null || enrollmentData.getEnrollmentId() == null) {
            sLogger.v("Enrollment data or id is not found for ad tech: %s", adTech);
            return Collections.emptyList();
        }

        sLogger.v("Fetching signature keys for %s", enrollmentData.getEnrollmentId());
        List<EncryptionKey> encryptionKeys =
                mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentData.getEnrollmentId(), EncryptionKey.KeyType.SIGNING);

        sLogger.v("Received %s signing key(s)", encryptionKeys.size());
        Base64.Decoder decoder = Base64.getDecoder();
        return encryptionKeys.stream()
                .sorted(Comparator.comparingLong(EncryptionKey::getExpiration))
                .map(key -> decoder.decode(key.getBody()))
                .collect(Collectors.toList());
    }
}
