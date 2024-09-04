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

package com.android.adservices.service.encryptionkey;

import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit tests for {@link EncryptionKey} */
public final class EncryptionKeyTest extends AdServicesUnitTestCase {

    private static final String ID = "1";
    private static final String ENROLLMENT_ID1 = "10";
    private static final String ENROLLMENT_ID2 = "11";
    private static final Uri REPORTING_ORIGIN = Uri.parse("https://test1.com/trigger");
    private static final String ENCRYPTION_KEY_URL = "https://test1.com/encryption-keys";
    private static final int KEY_COMMITMENT_ID = 1;
    private static final String BODY = "WVZBTFVF";
    private static final long EXPIRATION = 100000L;
    private static final long LAST_FETCH_TIME = 12345L;

    private static EncryptionKey createKeyCommitment(String enrollmentId) {
        return new EncryptionKey.Builder()
                .setId(ID)
                .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                .setEnrollmentId(enrollmentId)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setEncryptionKeyUrl(ENCRYPTION_KEY_URL)
                .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                .setKeyCommitmentId(KEY_COMMITMENT_ID)
                .setBody(BODY)
                .setExpiration(EXPIRATION)
                .setLastFetchTime(LAST_FETCH_TIME)
                .build();
    }

    /** Unit test for encryption key creation using builder. */
    @Test
    public void testCreation() throws Exception {
        EncryptionKey result = createKeyCommitment(ENROLLMENT_ID1);

        expect.that(result.getId()).isEqualTo(ID);
        expect.that(result.getKeyType()).isEqualTo(EncryptionKey.KeyType.ENCRYPTION);
        expect.that(result.getEnrollmentId()).isEqualTo(ENROLLMENT_ID1);
        expect.that(result.getReportingOrigin()).isEqualTo(REPORTING_ORIGIN);
        expect.that(result.getEncryptionKeyUrl()).isEqualTo(ENCRYPTION_KEY_URL);
        expect.that(result.getProtocolType()).isEqualTo(EncryptionKey.ProtocolType.HPKE);
        expect.that(result.getKeyCommitmentId()).isEqualTo(KEY_COMMITMENT_ID);
        expect.that(result.getBody()).isEqualTo(BODY);
        expect.that(result.getExpiration()).isEqualTo(EXPIRATION);
        expect.that(result.getLastFetchTime()).isEqualTo(LAST_FETCH_TIME);
    }

    /** Unit test for encryption key default creation. */
    @Test
    public void testDefaults() throws Exception {
        EncryptionKey result = new EncryptionKey.Builder().build();

        expect.that(result.getId()).isNull();
        expect.that(result.getKeyType()).isEqualTo(EncryptionKey.KeyType.ENCRYPTION);
        expect.that(result.getEnrollmentId()).isNull();
        expect.that(result.getReportingOrigin()).isNull();
        expect.that(result.getEncryptionKeyUrl()).isNull();
        expect.that(result.getProtocolType()).isEqualTo(EncryptionKey.ProtocolType.HPKE);
        expect.that(result.getKeyCommitmentId()).isEqualTo(0);
        expect.that(result.getBody()).isNull();
        expect.that(result.getExpiration()).isEqualTo(0L);
        expect.that(result.getLastFetchTime()).isEqualTo(0L);
    }

    /** Unit test for encryption key hashcode equals. */
    @Test
    public void testHashCode_equals() throws Exception {
        EncryptionKey result1 = createKeyCommitment(ENROLLMENT_ID1);
        EncryptionKey result2 = createKeyCommitment(ENROLLMENT_ID1);
        EncryptionKey result3 = createKeyCommitment(ENROLLMENT_ID2);

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(result1, result2);
        et.expectObjectsAreNotEqual(result1, result3);
    }
}
