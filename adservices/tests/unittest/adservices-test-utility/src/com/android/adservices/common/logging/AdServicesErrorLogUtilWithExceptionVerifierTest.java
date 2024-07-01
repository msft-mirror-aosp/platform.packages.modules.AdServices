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
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Undefined;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCalls;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;

import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;

public final class AdServicesErrorLogUtilWithExceptionVerifierTest
        extends AdServicesMockitoTestCase {
    @Mock private Description mMockDescription;
    @Mock private ExpectErrorLogUtilWithExceptionCall mExpectErrorLogUtilWithExceptionCall1;
    @Mock private ExpectErrorLogUtilWithExceptionCall mExpectErrorLogUtilWithExceptionCall2;
    @Mock private ExpectErrorLogUtilWithExceptionCalls mExpectErrorLogUtilWithExceptionCalls;
    @Mock private SetErrorLogUtilDefaultParams mDefaultParams;

    private final AdServicesErrorLogUtilWithExceptionVerifier mErrorLogUtilVerifier =
            new AdServicesErrorLogUtilWithExceptionVerifier();

    @Test
    public void testGetExpectedLogCalls_withNoAnnotations_returnsEmptySet() {
        when(mMockDescription.getAnnotation(any())).thenReturn(null);

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription)).isEmpty();
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotation_returnsNonEmptyList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        ErrorLogUtilCall errorLogUtilCall =
                mockAnnotationAndInitErrorLogUtilCall(
                        mExpectErrorLogUtilWithExceptionCall1,
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationNegativeTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        when(mExpectErrorLogUtilWithExceptionCall1.times()).thenReturn(-1);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilWithExceptionCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationZeroTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        when(mExpectErrorLogUtilWithExceptionCall1.times()).thenReturn(0);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilWithExceptionCall with times = 0. Remove "
                                + "annotation as the test will automatically fail if any log "
                                + "calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingThrowable_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall1,
                /* throwable= */ Undefined.class,
                /* errorCode= */ 20,
                /* ppapiName= */ 2,
                /* times= */ 1);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve throwable for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingErrorCode_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall1,
                /* throwable= */ Any.class,
                /* errorCode= */ UNDEFINED_INT_PARAM,
                /* ppapiName= */ 2,
                /* times= */ 1);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve errorCode for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingPpapiName_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall1,
                /* throwable= */ Any.class,
                /* errorCode= */ 2,
                /* ppapiName= */ UNDEFINED_INT_PARAM,
                /* times= */ 1);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve ppapiName for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationAndDefaultParams_returnsNonEmptyList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCall1);
        when(mMockDescription.getAnnotation(SetErrorLogUtilDefaultParams.class))
                .thenReturn(mDefaultParams);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall1,
                /* throwable= */ Undefined.class,
                /* errorCode= */ UNDEFINED_INT_PARAM,
                /* ppapiName= */ 30,
                /* times= */ 1);
        mockDefaultParams(
                /* throwable= */ Any.class,
                /* errorCode= */ 90,
                /* ppapiName= */ UNDEFINED_INT_PARAM);

        mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription);

        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(
                        /* throwable= */ Any.class, /* errorCode= */ 90, /* ppapiName= */ 30);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleValidAnnotations_returnsNonEmptyList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCalls.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCalls);
        when(mExpectErrorLogUtilWithExceptionCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilWithExceptionCall[] {
                            mExpectErrorLogUtilWithExceptionCall1,
                            mExpectErrorLogUtilWithExceptionCall2
                        });
        ErrorLogUtilCall errorLogUtilCall1 =
                mockAnnotationAndInitErrorLogUtilCall(
                        mExpectErrorLogUtilWithExceptionCall1,
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                mockAnnotationAndInitErrorLogUtilCall(
                        mExpectErrorLogUtilWithExceptionCall2,
                        /* throwable= */ Any.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);

        // Even though both invocations are the same, they are not equal. Therefore,
        // no exception should be thrown calling to de-dupe annotations using times.
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationZeroTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCalls.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCalls);
        when(mExpectErrorLogUtilWithExceptionCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilWithExceptionCall[] {
                            mExpectErrorLogUtilWithExceptionCall1,
                            mExpectErrorLogUtilWithExceptionCall2
                        });
        when(mExpectErrorLogUtilWithExceptionCall1.times()).thenReturn(1);
        when(mExpectErrorLogUtilWithExceptionCall2.times()).thenReturn(0);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilWithExceptionCall with times = 0. Remove "
                                + "annotation as the test will automatically fail if any log "
                                + "calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationNegativeTimes_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCalls.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCalls);
        when(mExpectErrorLogUtilWithExceptionCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilWithExceptionCall[] {
                            mExpectErrorLogUtilWithExceptionCall1,
                            mExpectErrorLogUtilWithExceptionCall2
                        });
        when(mExpectErrorLogUtilWithExceptionCall1.times()).thenReturn(-2);

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilWithExceptionCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationsSameInvocation_throwsException() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCalls.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCalls);
        when(mExpectErrorLogUtilWithExceptionCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilWithExceptionCall[] {
                            mExpectErrorLogUtilWithExceptionCall1,
                            mExpectErrorLogUtilWithExceptionCall2
                        });
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall1,
                /* throwable= */ IllegalArgumentException.class,
                /* errorCode= */ 30,
                /* ppapiName= */ 20,
                /* times= */ 1);
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall2,
                /* throwable= */ IllegalArgumentException.class,
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
                        "Detected @ExpectErrorLogUtilWithExceptionCall annotations representing "
                                + "the same invocation! De-dupe by using times arg");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationsAndDefaultParams_returnsList() {
        when(mMockDescription.getAnnotation(ExpectErrorLogUtilWithExceptionCalls.class))
                .thenReturn(mExpectErrorLogUtilWithExceptionCalls);
        when(mExpectErrorLogUtilWithExceptionCalls.value())
                .thenReturn(
                        new ExpectErrorLogUtilWithExceptionCall[] {
                            mExpectErrorLogUtilWithExceptionCall1,
                            mExpectErrorLogUtilWithExceptionCall2
                        });
        when(mMockDescription.getAnnotation(SetErrorLogUtilDefaultParams.class))
                .thenReturn(mDefaultParams);
        // First annotation missing throwable
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall1,
                /* throwable= */ Undefined.class,
                /* errorCode= */ 30,
                /* ppapiName= */ 20,
                /* times= */ 1);
        // Second annotation missing errorCode
        mockAnnotationAndInitErrorLogUtilCall(
                mExpectErrorLogUtilWithExceptionCall2,
                /* throwable= */ Any.class,
                /* errorCode= */ UNDEFINED_INT_PARAM,
                /* ppapiName= */ 30,
                /* times= */ 1);
        // Set default params
        mockDefaultParams(
                /* throwable= */ NullPointerException.class,
                /* errorCode= */ 90,
                /* ppapiName= */ 100);

        mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription);

        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(
                        /* throwable= */ NullPointerException.class,
                        /* errorCode= */ 30,
                        /* ppapiName= */ 20,
                        /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(
                        /* throwable= */ Any.class,
                        /* errorCode= */ 90,
                        /* ppapiName= */ 30,
                        /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(mMockDescription))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testResolutionMessage() {
        expect.that(mErrorLogUtilVerifier.getResolutionMessage())
                .isEqualTo(
                        "Please make sure to use @ExpectErrorLogUtilWithExceptionCall(..) over "
                                + "test method to denote all expected ErrorLogUtil.e(Throwable, "
                                + "int, int) calls.");
    }

    private ErrorLogUtilCall mockAnnotationAndInitErrorLogUtilCall(
            ExpectErrorLogUtilWithExceptionCall annotation,
            Class<? extends Throwable> throwable,
            int errorCode,
            int ppapiName,
            int times) {
        // Mock annotation
        doReturn(throwable).when(annotation).throwable();
        when(annotation.errorCode()).thenReturn(errorCode);
        when(annotation.ppapiName()).thenReturn(ppapiName);
        when(annotation.times()).thenReturn(times);

        // Return corresponding ErrorLogUtilCall object that is usually needed for verification
        return new ErrorLogUtilCall(throwable, errorCode, ppapiName, times);
    }

    private void mockDefaultParams(
            Class<? extends Throwable> throwable, int errorCode, int ppapiName) {
        doReturn(throwable).when(mDefaultParams).throwable();
        when(mDefaultParams.errorCode()).thenReturn(errorCode);
        when(mDefaultParams.ppapiName()).thenReturn(ppapiName);
    }
}
