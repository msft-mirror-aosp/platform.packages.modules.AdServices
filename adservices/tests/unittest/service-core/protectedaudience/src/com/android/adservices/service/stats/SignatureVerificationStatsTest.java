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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SignatureVerificationStatsTest {
    @Test
    public void testSignatureVerificationStats_valuesUnset_success() {
        SignatureVerificationStats stats = SignatureVerificationStats.builder().build();

        assertEquals(
                SignatureVerificationStats.VerificationStatus.UNKNOWN,
                stats.getSignatureVerificationStatus());
        assertEquals(UNSET, stats.getKeyFetchLatency());
        assertEquals(UNSET, stats.getSerializationLatency());
        assertEquals(UNSET, stats.getVerificationLatency());
        assertEquals(UNSET, stats.getNumOfKeysFetched());
        assertEquals(EMPTY_STRING, stats.getFailedSignatureSellerEnrollmentId());
        assertEquals(EMPTY_STRING, stats.getFailedSignatureBuyerEnrollmentId());
        assertEquals(EMPTY_STRING, stats.getFailedSignatureCallerPackageName());
        assertEquals(UNSET, stats.getFailureDetailUnknownError());
        assertEquals(UNSET, stats.getFailureDetailNoEnrollmentDataForBuyer());
        assertEquals(UNSET, stats.getFailureDetailNoKeysFetchedForBuyer());
        assertEquals(UNSET, stats.getFailureDetailWrongSignatureFormat());
        assertEquals(UNSET, stats.getFailureDetailCountOfKeysWithWrongFormat());
        assertEquals(UNSET, stats.getFailureDetailCountOfKeysFailedToVerifySignature());
    }
}
