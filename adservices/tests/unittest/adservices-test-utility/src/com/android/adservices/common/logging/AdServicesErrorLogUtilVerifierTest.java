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

package com.android.adservices.common.logging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;

import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;

public final class AdServicesErrorLogUtilVerifierTest extends AdServicesMockitoTestCase {
    @Mock private Description mMockDescription;
    @Mock private ExpectErrorLogUtilCall mExpectErrorLogUtilCall;

    private final AdServicesErrorLogUtilVerifier mErrorLogUtilVerifier =
            new AdServicesErrorLogUtilVerifier();

    @Test
    public void testGetExpectedLogCalls_withNoAnnotations_returnsEmptySet() {
        when(mMockDescription.getAnnotation(any())).thenReturn(null);

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription)).isEmpty();
    }

    @Test
    public void testGetExpectedLogCalls_withAnnotationExists_returnsListWithErrorLogUtilCall() {
        when(mMockDescription.getAnnotation(any())).thenReturn(mExpectErrorLogUtilCall);
        doReturn(IllegalArgumentException.class).when(mExpectErrorLogUtilCall).throwable();
        when(mExpectErrorLogUtilCall.times()).thenReturn(1);
        when(mExpectErrorLogUtilCall.errorCode()).thenReturn(15);
        when(mExpectErrorLogUtilCall.ppapiName()).thenReturn(10);

        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall);
    }
}
