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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCalls;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;

import com.google.auto.value.AutoAnnotation;

import org.junit.Test;
import org.junit.runner.Description;

public final class AdServicesErrorLogUtilVerifierTest extends AdServicesUnitTestCase {
    private static final int DEFAULT_ERROR_CODE = 100;
    private static final int DEFAULT_PPAPI = 200;

    private final AdServicesErrorLogUtilVerifier mErrorLogUtilVerifier =
            new AdServicesErrorLogUtilVerifier();

    @Test
    public void testGetExpectedLogCalls_withNoAnnotations_returnsEmptySet() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class, "test");

        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description)).isEmpty();
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotation_returnsNonEmptyList() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallAnnotation(
                                /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 1));

        ErrorLogUtilCall errorLogUtilCall =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationNegativeTimes_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallAnnotation(
                                /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ -1));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationZeroTimes_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallAnnotation(
                                /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 0));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilCall with times = 0. Remove annotation as the "
                                + "test will automatically fail if any log calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingErrorCode_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallAnnotation(
                                /* errorCode= */ UNDEFINED_INT_PARAM,
                                /* ppapiName= */ 2,
                                /* times= */ 1));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve errorCode for @ExpectErrorLogUtilCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingPpapiName_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallAnnotation(
                                /* errorCode= */ 2,
                                /* ppapiName= */ UNDEFINED_INT_PARAM,
                                /* times= */ 1));
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve ppapiName for @ExpectErrorLogUtilCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationAndDefaultParams_returnsNonEmptyList() {
        Description description =
                Description.createTestDescription(
                        AClassWithSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallAnnotation(
                                /* errorCode= */ UNDEFINED_INT_PARAM,
                                /* ppapiName= */ UNDEFINED_INT_PARAM,
                                /* times= */ 1));

        ErrorLogUtilCall errorLogUtilCall =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ DEFAULT_ERROR_CODE,
                        /* ppapiName= */ DEFAULT_PPAPI,
                        /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleValidAnnotations_returnsNonEmptyList() {
        ExpectErrorLogUtilCall annotation1 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 1);
        ExpectErrorLogUtilCall annotation2 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 20, /* times= */ 1);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallsAnnotation(
                                new ExpectErrorLogUtilCall[] {annotation1, annotation2}));

        ErrorLogUtilCall errorLogUtilCall1 =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 15, /* ppapiName= */ 20, /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationZeroTimes_throwsException() {
        ExpectErrorLogUtilCall annotation1 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 1);
        ExpectErrorLogUtilCall annotation2 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 20, /* times= */ 0);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallsAnnotation(
                                new ExpectErrorLogUtilCall[] {annotation1, annotation2}));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilCall with times = 0. Remove annotation as the "
                                + "test will automatically fail if any log calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationNegativeTimes_throwsException() {
        ExpectErrorLogUtilCall annotation1 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ -2);
        ExpectErrorLogUtilCall annotation2 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 20, /* times= */ 1);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallsAnnotation(
                                new ExpectErrorLogUtilCall[] {annotation1, annotation2}));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationsSameInvocation_throwsException() {
        ExpectErrorLogUtilCall annotation1 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 1);
        ExpectErrorLogUtilCall annotation2 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 15, /* ppapiName= */ 10, /* times= */ 2);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallsAnnotation(
                                new ExpectErrorLogUtilCall[] {annotation1, annotation2}));

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

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
        ExpectErrorLogUtilCall annotation1 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ UNDEFINED_INT_PARAM, /* ppapiName= */ 20, /* times= */ 1);
        ExpectErrorLogUtilCall annotation2 =
                newExpectErrorLogUtilCallAnnotation(
                        /* errorCode= */ 30, /* ppapiName= */ UNDEFINED_INT_PARAM, /* times= */ 1);

        Description description =
                Description.createTestDescription(
                        AClassWithSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilCallsAnnotation(
                                new ExpectErrorLogUtilCall[] {annotation1, annotation2}));

        ErrorLogUtilCall errorLogUtilCall1 =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ DEFAULT_ERROR_CODE, /* ppapiName= */ 20, /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                ErrorLogUtilCall.createWithNoException(
                        /* errorCode= */ 30, /* ppapiName= */ DEFAULT_PPAPI, /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testResolutionMessage() {
        expect.that(mErrorLogUtilVerifier.getResolutionMessage())
                .isEqualTo(
                        "Please make sure to use @ExpectErrorLogUtilCall(..) over test method to"
                                + " denote all expected ErrorLogUtil.e(int, int) calls.");
    }

    private static class AClassWithNoSetDefaultParamsAnnotation {}

    @SetErrorLogUtilDefaultParams(errorCode = DEFAULT_ERROR_CODE, ppapiName = DEFAULT_PPAPI)
    private static class AClassWithSetDefaultParamsAnnotation {}

    @AutoAnnotation
    private static ExpectErrorLogUtilCall newExpectErrorLogUtilCallAnnotation(
            int errorCode, int ppapiName, int times) {
        return new AutoAnnotation_AdServicesErrorLogUtilVerifierTest_newExpectErrorLogUtilCallAnnotation(
                errorCode, ppapiName, times);
    }

    @AutoAnnotation
    private static ExpectErrorLogUtilCalls newExpectErrorLogUtilCallsAnnotation(
            ExpectErrorLogUtilCall[] value) {
        return new AutoAnnotation_AdServicesErrorLogUtilVerifierTest_newExpectErrorLogUtilCallsAnnotation(
                value);
    }
}
