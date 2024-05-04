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

package com.android.adservices.shared.errorlogging;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.util.Base64;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.proto.ErrorCodeList;
import com.android.adservices.shared.proto.ErrorCodeSampleInterval;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Random;

public final class ErrorCodeSamplerTest extends AdServicesMockitoTestCase {
    private static final ErrorCodeSampleInterval SAMPLE_INTERVAL =
            ErrorCodeSampleInterval.newBuilder()
                    .setDefaultSampleInterval(10)
                    .putSampleIntervalToErrorCodes(
                            100,
                            ErrorCodeList.newBuilder().addErrorCode(200).addErrorCode(300).build())
                    .build();

    private static final String ENCODED_ERROR_CODE_SAMPLE_RATE =
            Base64.encodeToString(SAMPLE_INTERVAL.toByteArray(), Base64.DEFAULT);

    @Mock private ModuleSharedFlags mFlags;
    @Mock private Random mRandom;

    @Before
    public void setUp() {
        when(mFlags.getEncodedErrorCodeListPerSampleInterval())
                .thenReturn(ENCODED_ERROR_CODE_SAMPLE_RATE);
    }

    @Test
    public void testShouldLog_randomSampling_loggingSuccess() {
        when(mRandom.nextInt(anyInt())).thenReturn(1);
        ErrorCodeSampler errorCodeSampler = new ErrorCodeSampler(mFlags, mRandom);

        int errorCode = 200;
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isTrue();

        errorCode = 700;
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isTrue();
    }

    @Test
    public void testShouldLog_noSampling_errorCodeNotPresent() {
        ErrorCodeSampleInterval sampleInterval =
                ErrorCodeSampleInterval.newBuilder()
                        .putSampleIntervalToErrorCodes(
                                100,
                                ErrorCodeList.newBuilder()
                                        .addErrorCode(200)
                                        .addErrorCode(300)
                                        .build())
                        .build();
        when(mFlags.getEncodedErrorCodeListPerSampleInterval())
                .thenReturn(Base64.encodeToString(sampleInterval.toByteArray(), Base64.DEFAULT));
        ErrorCodeSampler errorCodeSampler = new ErrorCodeSampler(mFlags);

        // Error code not present in map and default sampling rate is not defined.
        int errorCode = 101;
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isTrue();

        when(mFlags.getEncodedErrorCodeListPerSampleInterval()).thenReturn("");
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isTrue();
    }

    @Test
    public void testShouldLog_noSampling_emptyMap() {
        when(mFlags.getEncodedErrorCodeListPerSampleInterval()).thenReturn("");
        ErrorCodeSampler errorCodeSampler = new ErrorCodeSampler(mFlags);
        int errorCode = 101;

        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isTrue();
    }

    @Test
    public void testShouldLog_randomSampling_doesNotLog() {
        when(mRandom.nextInt(anyInt())).thenReturn(10);
        ErrorCodeSampler errorCodeSampler = new ErrorCodeSampler(mFlags, mRandom);

        // Error code present
        int errorCode = 200;
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isFalse();

        errorCode = 300;
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isFalse();

        // Error code not present, default sampling does not allow logging due to out of sample
        // interval.
        errorCode = 101;
        expect.withMessage("shouldLog(errorCode=%s)", errorCode)
                .that(errorCodeSampler.shouldLog(errorCode))
                .isFalse();
    }
}
