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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall.UNDEFINED_INT_PARAM;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCalls;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;

import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;

public final class AdServicesErrorLogUtilVerifierTest extends AdServicesMockitoTestCase {
    @Mock private Description mMockDescription;
    @Mock private ExpectErrorLogUtilCall mExpectErrorLogUtilCall1;
    @Mock private ExpectErrorLogUtilCall mExpectErrorLogUtilCall2;
    @Mock private ExpectErrorLogUtilCalls mExpectErrorLogUtilCalls;
    @Mock private SetErrorLogUtilDefaultParams mDefaultParams;

    private final AdServicesErrorLogUtilVerifier mErrorLogUtilVerifier =
            new AdServicesErrorLogUtilVerifier();

    @Test
    public void testGetExpectedLogCalls_withNoAnnotations_returnsEmptySet() {
        when(mMockDescription.getAnnotation(any())).thenReturn(null);

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription)).isEmpty();
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotation_returnsNonEmptyList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCall.class))
                .thenReturn(mExpectErrorLogUtilCall1);
        ErrorLogUtilCall errorLogUtilCall =
                mockAnnotationAndInitErrorLogUtilCall(
                        mExpectErrorLogUtilCall1,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationNegativeTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCall.class))
                .thenReturn(mExpectErrorLogUtilCall1);
        when(mExpectErrorLogUtilCall1.times()).thenReturn(-1);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationZeroTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCall.class))
                .thenReturn(mExpectErrorLogUtilCall1);
        when(mExpectErrorLogUtilCall1.times()).thenReturn(0);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilCall with times = 0. Remove annotation as the "
                                + "test will automatically fail if any log calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingErrorCode_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCall.class))
                .thenReturn(mExpectErrorLogUtilCall1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall1,
                /* errorCode= */ UNDEFINED_INT_PARAM,
                /* ppapiName= */ 2,
                /* times= */ 1);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve errorCode for @ExpectErrorLogUtilCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingPpapiName_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCall.class))
                .thenReturn(mExpectErrorLogUtilCall1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall1,
                /* errorCode= */ 2,
                /* ppapiName= */ UNDEFINED_INT_PARAM,
                /* times= */ 1);
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve ppapiName for @ExpectErrorLogUtilCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationAndDefaultParams_returnsNonEmptyList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCall.class))
                .thenReturn(mExpectErrorLogUtilCall1);
        when(mMockDescription.getAnnotation(SetErrorLogUtilDefaultParams.class))
                .thenReturn(mDefaultParams);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall1,
                /* errorCode= */ UNDEFINED_INT_PARAM,
                /* ppapiName= */ UNDEFINED_INT_PARAM,
                /* times= */ 1);
        mockDefaultParams(/* errorCode= */ 20, /* ppapiName= */ 30);

        mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription);

        ErrorLogUtilCall errorLogUtilCall =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 20, /* ppapiName= */ 30, /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleValidAnnotations_returnsNonEmptyList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCalls.class))
                .thenReturn(mExpectErrorLogUtilCalls);
        when(mExpectErrorLogUtilCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilCall[] {
                            mExpectErrorLogUtilCall1, mExpectErrorLogUtilCall2
                        });
        ErrorLogUtilCall errorLogUtilCall1 =
                mockAnnotationAndInitErrorLogUtilCall(
                        mExpectErrorLogUtilCall1,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                mockAnnotationAndInitErrorLogUtilCall(
                        mExpectErrorLogUtilCall2,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 20,
                        /* times= */ 1);

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationZeroTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCalls.class))
                .thenReturn(mExpectErrorLogUtilCalls);
        when(mExpectErrorLogUtilCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilCall[] {
                            mExpectErrorLogUtilCall1, mExpectErrorLogUtilCall2
                        });
        when(mExpectErrorLogUtilCall1.times()).thenReturn(1);
        when(mExpectErrorLogUtilCall2.times()).thenReturn(0);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilCall with times = 0. Remove annotation as the "
                                + "test will automatically fail if any log calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationNegativeTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCalls.class))
                .thenReturn(mExpectErrorLogUtilCalls);
        when(mExpectErrorLogUtilCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilCall[] {
                            mExpectErrorLogUtilCall1, mExpectErrorLogUtilCall2
                        });
        when(mExpectErrorLogUtilCall1.times()).thenReturn(-2);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationsSameInvocation_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCalls.class))
                .thenReturn(mExpectErrorLogUtilCalls);
        when(mExpectErrorLogUtilCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilCall[] {
                            mExpectErrorLogUtilCall1, mExpectErrorLogUtilCall2
                        });
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall1,
                /* errorCode= */ 30,
                /* ppapiName= */ 20,
                /* times= */ 1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall2,
                /* errorCode= */ 30,
                /* ppapiName= */ 20,
                /* times= */ 2);

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        // Even though times is different for both invocations, an exception is expected because
        // the annotations can be de-duped using times arg.
        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilCall annotations representing the same "
                                + "invocation! De-dupe by using times arg");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationsAndDefaultParams_returnsList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilCalls.class))
                .thenReturn(mExpectErrorLogUtilCalls);
        when(mExpectErrorLogUtilCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilCall[] {
                            mExpectErrorLogUtilCall1, mExpectErrorLogUtilCall2
                        });
        when(mMockDescription.getAnnotation(SetErrorLogUtilDefaultParams.class))
                .thenReturn(mDefaultParams);
        // First annotation missing errorCode
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall1,
                /* errorCode= */ UNDEFINED_INT_PARAM,
                /* ppapiName= */ 20,
                /* times= */ 1);
        // Second annotation missing ppapiName
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilCall2,
                /* errorCode= */ 30,
                /* ppapiName= */ UNDEFINED_INT_PARAM,
                /* times= */ 1);
        // Set default params
        mockDefaultParams(/* errorCode= */ 90, /* ppapiName= */ 100);

        mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription);

        ErrorLogUtilCall errorLogUtilCall1 =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 90, /* ppapiName= */ 20, /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 30, /* ppapiName= */ 100, /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testResolutionMessage() {
        expect.that(mErrorLogUtilVerifier.getResolutionMessage())
                .isEqualTo(
                        "Please make sure to use @ExpectErrorLogUtilCall(..) over test method to"
                                + " denote all expected ErrorLogUtil.e(int, int) calls.");
    }

    private ErrorLogUtilCall mockAnnotationAndInitErrorLogUtilCall(
            ExpectErrorLogUtilCall annotation,
            int errorCode,
            int ppapiName,
            int times) {
        // Mock annotation
        when(annotation.errorCode()).thenReturn(errorCode);
        when(annotation.ppapiName()).thenReturn(ppapiName);
        when(annotation.times()).thenReturn(times);

        // Return corresponding ErrorLogUtilCall object that is usually needed for verification
        return ErrorLogUtilCall.createWithNoException(errorCode, ppapiName, times);
    }

    private void mockDefaultParams(int errorCode, int ppapiName) {
        when(mDefaultParams.errorCode()).thenReturn(errorCode);
        when(mDefaultParams.ppapiName()).thenReturn(ppapiName);
    }
}
