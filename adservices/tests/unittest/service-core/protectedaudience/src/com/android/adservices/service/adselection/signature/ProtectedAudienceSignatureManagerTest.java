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

import static android.adservices.adselection.SignedContextualAdsFixture.aSignedContextualAds;

import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.EMPTY_STRING_FOR_MISSING_ENROLLMENT_ID;
import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.PUBLIC_TEST_KEY_STRING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.adselection.SignedContextualAds;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.SignatureVerificationLogger;
import com.android.adservices.service.stats.SignatureVerificationStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProtectedAudienceSignatureManagerTest {
    private static final SignedContextualAds SIGNED_CONTEXTUAL_ADS = aSignedContextualAds();
    private static final String BUYER_ENROLLMENT_ID = "buyer-enrollment-id";
    private static final String SELLER_ENROLLMENT_ID = "seller-enrollment-id";
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("seller.com");
    private static final String CALLER_PACKAGE_NAME = "callerPackageName";
    private static final List<String> SINGLE_KEY =
            Collections.singletonList(PUBLIC_TEST_KEY_STRING);
    private static final List<String> MULTIPLE_KEYS =
            List.of(
                    generateRandomECPublicKey(),
                    PUBLIC_TEST_KEY_STRING,
                    generateRandomECPublicKey());
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncryptionKeyDao mEncryptionKeyDaoMock;
    @Mock private SignatureVerificationLogger mSignatureVerificationLoggerMock;
    private ProtectedAudienceSignatureManager mSignatureManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        boolean enrollmentEnabled = true;
        mSignatureManager =
                new ProtectedAudienceSignatureManager(
                        mEnrollmentDaoMock,
                        mEncryptionKeyDaoMock,
                        mSignatureVerificationLoggerMock,
                        enrollmentEnabled);
    }

    @Test
    public void testVerifySignature_validSignature_returnTrue() {
        mockKeyStorage(SINGLE_KEY);

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, SIGNED_CONTEXTUAL_ADS);

        assertThat(isVerified).isTrue();
        int expectedNumOfKeysFailedVerifyingSignature = 0;
        verifyLogSuccessfulSignatureVerification(
                SINGLE_KEY.size(), expectedNumOfKeysFailedVerifyingSignature);
    }

    @Test
    public void testVerifySignature_invalidSignature_returnFalse() {
        byte[] invalidSignature = new byte[] {1, 2, 3};
        SignedContextualAds signedContextualAds =
                new SignedContextualAds.Builder(SIGNED_CONTEXTUAL_ADS)
                        .setSignature(invalidSignature)
                        .build();
        mockKeyStorage(SINGLE_KEY);

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, signedContextualAds);

        assertThat(isVerified).isFalse();
        verifyLogFailedSignatureVerification(SINGLE_KEY.size());
    }

    @Test
    public void testMultipleKeys_success() {
        mockKeyStorage(MULTIPLE_KEYS);

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, SIGNED_CONTEXTUAL_ADS);

        int expectedNumOfKeysFailedVerifyingSignature = 1;
        assertThat(isVerified).isTrue();
        verifyLogSuccessfulSignatureVerification(
                MULTIPLE_KEYS.size(), expectedNumOfKeysFailedVerifyingSignature);
    }

    @Test
    public void testFetchKeys_adTechNotEnrolled_failsVerification() {
        doReturn(null).when(mEnrollmentDaoMock).getEnrollmentDataForFledgeByAdTechIdentifier(BUYER);

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, SIGNED_CONTEXTUAL_ADS);

        assertThat(isVerified).isFalse();
        verifyLogFailedSignatureVerificationAdTechNotEnrolled();
    }

    @Test
    public void testFetchKeys_adTechEnrollmentIdNull_failsVerification() {
        doReturn(new EnrollmentData.Builder().build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(BUYER);

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, SIGNED_CONTEXTUAL_ADS);

        assertThat(isVerified).isFalse();
        verifyLogFailedSignatureVerificationAdTechNotEnrolled();
    }

    @Test
    public void testFetchKeys_adTechHasNoKeys_failsVerification() {
        mockKeyStorage(Collections.emptyList());

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, SIGNED_CONTEXTUAL_ADS);

        assertThat(isVerified).isFalse();
        verifyLogFailedSignatureVerificationAdTechHasNoKeys();
    }

    @Test
    public void testFetchKeys_adTechHasKeyWithWrongFormat_failsVerification() {
        byte[] keyWithWrongFormat = new byte[] {1, 2, 3};
        mockKeyStorage(
                Collections.singletonList(Base64.getEncoder().encodeToString(keyWithWrongFormat)));

        boolean isVerified =
                mSignatureManager.isVerified(
                        BUYER, SELLER, CALLER_PACKAGE_NAME, SIGNED_CONTEXTUAL_ADS);

        assertThat(isVerified).isFalse();
        verifyLogFailedSignatureVerificationAdTechHasKeyWithWrongFormat();
    }

    @Test
    public void testFetchKeys_validAdTechMultipleKeys_returnedInAscendingOrderByExpiration() {
        AdTechIdentifier adTech = AdTechIdentifier.fromString("example.com");
        String enrollmentId = "enrollment1";
        EnrollmentData enrollment =
                new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();

        byte[] publicKeyBytes1 = new byte[] {1, 2, 3, 4, 5};
        String publicKey1 = Base64.getEncoder().encodeToString(publicKeyBytes1);
        byte[] publicKeyBytes2 = new byte[] {6, 7, 8, 9, 10};
        String publicKey2 = Base64.getEncoder().encodeToString(publicKeyBytes2);
        long expiration1 = 0L;
        long expiration2 = 1L;
        EncryptionKey encKey1 =
                new EncryptionKey.Builder().setBody(publicKey1).setExpiration(expiration1).build();
        EncryptionKey encKey2 =
                new EncryptionKey.Builder().setBody(publicKey2).setExpiration(expiration2).build();
        List<EncryptionKey> encKeysToPersistInReverseOrder = List.of(encKey2, encKey1);

        doReturn(enrollment)
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
        doReturn(encKeysToPersistInReverseOrder)
                .when(mEncryptionKeyDaoMock)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        enrollmentId, EncryptionKey.KeyType.SIGNING);

        List<byte[]> signingKeys = mSignatureManager.fetchPublicKeyForAdTech(adTech);

        assertThat(signingKeys.size()).isEqualTo(2);
        assertThat(signingKeys.get(0)).isEqualTo(publicKeyBytes1);
        assertThat(signingKeys.get(1)).isEqualTo(publicKeyBytes2);
    }

    private void verifyLogSuccessfulSignatureVerification(
            int expectedNumOfKeysFetched, int expectedNumOfKeysFailedVerifyingSignature) {
        int verified = SignatureVerificationStats.VerificationStatus.VERIFIED.getValue();
        verify(mSignatureVerificationLoggerMock, times(1)).startKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .startSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .endSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).startSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .setNumOfKeysFetched(expectedNumOfKeysFetched);
        verify(mSignatureVerificationLoggerMock, times(1)).close(verified);
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailedSignatureBuyerEnrollmentId(anyString());
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailedSignatureSellerEnrollmentId(anyString());
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailedSignatureCallerPackageName(anyString());
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailUnknownError();
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailureDetailNoEnrollmentDataForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailNoKeysFetchedForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailWrongSignatureFormat();
        verify(mSignatureVerificationLoggerMock, times(0))
                .addFailureDetailCountOfKeysWithWrongFormat();
        verify(mSignatureVerificationLoggerMock, times(expectedNumOfKeysFailedVerifyingSignature))
                .addFailureDetailCountOfKeysFailedToVerifySignature();
    }

    private void verifyLogFailedSignatureVerification(int expectedNumOfKeysFetched) {
        int verified = SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED.getValue();
        verify(mSignatureVerificationLoggerMock, times(1)).startKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .startSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .endSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).startSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .setNumOfKeysFetched(expectedNumOfKeysFetched);
        verify(mSignatureVerificationLoggerMock, times(1)).close(verified);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureBuyerEnrollmentId(BUYER_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureSellerEnrollmentId(SELLER_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureCallerPackageName(CALLER_PACKAGE_NAME);
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailUnknownError();
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailureDetailNoEnrollmentDataForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailNoKeysFetchedForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailWrongSignatureFormat();
        verify(mSignatureVerificationLoggerMock, times(0))
                .addFailureDetailCountOfKeysWithWrongFormat();
        verify(mSignatureVerificationLoggerMock, times(expectedNumOfKeysFetched))
                .addFailureDetailCountOfKeysFailedToVerifySignature();
    }

    private void verifyLogFailedSignatureVerificationAdTechNotEnrolled() {
        int verified = SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED.getValue();
        verify(mSignatureVerificationLoggerMock, times(1)).startKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .startSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .endSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).startSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).setNumOfKeysFetched(0);
        verify(mSignatureVerificationLoggerMock, times(1)).close(verified);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureBuyerEnrollmentId(EMPTY_STRING_FOR_MISSING_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureSellerEnrollmentId(EMPTY_STRING_FOR_MISSING_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureCallerPackageName(CALLER_PACKAGE_NAME);
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailUnknownError();
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailureDetailNoEnrollmentDataForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailNoKeysFetchedForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailWrongSignatureFormat();
        verify(mSignatureVerificationLoggerMock, times(0))
                .addFailureDetailCountOfKeysWithWrongFormat();
        verify(mSignatureVerificationLoggerMock, times(0))
                .addFailureDetailCountOfKeysFailedToVerifySignature();
    }

    private void verifyLogFailedSignatureVerificationAdTechHasNoKeys() {
        int verified = SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED.getValue();
        verify(mSignatureVerificationLoggerMock, times(1)).startKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .startSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .endSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).startSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).setNumOfKeysFetched(0);
        verify(mSignatureVerificationLoggerMock, times(1)).close(verified);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureBuyerEnrollmentId(BUYER_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureSellerEnrollmentId(SELLER_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureCallerPackageName(CALLER_PACKAGE_NAME);
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailUnknownError();
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailureDetailNoEnrollmentDataForBuyer();
        verify(mSignatureVerificationLoggerMock, times(1)).setFailureDetailNoKeysFetchedForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailWrongSignatureFormat();
        verify(mSignatureVerificationLoggerMock, times(0))
                .addFailureDetailCountOfKeysWithWrongFormat();
        verify(mSignatureVerificationLoggerMock, times(0))
                .addFailureDetailCountOfKeysFailedToVerifySignature();
    }

    private void verifyLogFailedSignatureVerificationAdTechHasKeyWithWrongFormat() {
        int verified = SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED.getValue();
        verify(mSignatureVerificationLoggerMock, times(1)).startKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endKeyFetchForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .startSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1))
                .endSerializationForSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).startSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).endSignatureVerification();
        verify(mSignatureVerificationLoggerMock, times(1)).setNumOfKeysFetched(1);
        verify(mSignatureVerificationLoggerMock, times(1)).close(verified);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureBuyerEnrollmentId(BUYER_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureSellerEnrollmentId(SELLER_ENROLLMENT_ID);
        verify(mSignatureVerificationLoggerMock, times(1))
                .setFailedSignatureCallerPackageName(CALLER_PACKAGE_NAME);
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailUnknownError();
        verify(mSignatureVerificationLoggerMock, times(0))
                .setFailureDetailNoEnrollmentDataForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailNoKeysFetchedForBuyer();
        verify(mSignatureVerificationLoggerMock, times(0)).setFailureDetailWrongSignatureFormat();
        verify(mSignatureVerificationLoggerMock, times(1))
                .addFailureDetailCountOfKeysWithWrongFormat();
        verify(mSignatureVerificationLoggerMock, times(1))
                .addFailureDetailCountOfKeysFailedToVerifySignature();
    }

    private void mockKeyStorage(List<String> keys) {
        List<EncryptionKey> encryptionKeys =
                keys.stream()
                        .map(key -> new EncryptionKey.Builder().setBody(key).build())
                        .collect(Collectors.toList());
        doReturn(new EnrollmentData.Builder().setEnrollmentId(BUYER_ENROLLMENT_ID).build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(BUYER);
        doReturn(new EnrollmentData.Builder().setEnrollmentId(SELLER_ENROLLMENT_ID).build())
                .when(mEnrollmentDaoMock)
                .getEnrollmentDataForFledgeByAdTechIdentifier(SELLER);
        doReturn(encryptionKeys)
                .when(mEncryptionKeyDaoMock)
                .getEncryptionKeyFromEnrollmentIdAndKeyType(
                        BUYER_ENROLLMENT_ID, EncryptionKey.KeyType.SIGNING);
    }

    private static String generateRandomECPublicKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            return Base64.getEncoder()
                    .encodeToString(keyPairGenerator.generateKeyPair().getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Random public key generation failed!");
        }
    }
}
