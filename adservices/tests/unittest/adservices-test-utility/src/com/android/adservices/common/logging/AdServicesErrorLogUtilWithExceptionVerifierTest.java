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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCalls;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;

import com.google.auto.value.AutoAnnotation;

import org.junit.Test;
import org.junit.runner.Description;

public final class AdServicesErrorLogUtilWithExceptionVerifierTest extends AdServicesUnitTestCase {
    private static final Class<? extends Throwable> DEFAULT_THROWABLE = Any.class;
    private static final int DEFAULT_ERROR_CODE = 100;
    private static final int DEFAULT_PPAPI = 200;

    private final AdServicesErrorLogUtilWithExceptionVerifier mErrorLogUtilVerifier =
            new AdServicesErrorLogUtilWithExceptionVerifier();

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
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ IllegalArgumentException.class,
                                /* errorCode= */ 15,
                                /* ppapiName= */ 10,
                                /* times= */ 1));

        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationNegativeTimes_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ IllegalArgumentException.class,
                                /* errorCode= */ 15,
                                /* ppapiName= */ 10,
                                /* times= */ -1));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilWithExceptionCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationZeroTimes_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ IllegalArgumentException.class,
                                /* errorCode= */ 15,
                                /* ppapiName= */ 10,
                                /* times= */ 0));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilWithExceptionCall with times = 0. Remove "
                                + "annotation as the test will automatically fail if any log "
                                + "calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingThrowable_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ Undefined.class,
                                /* errorCode= */ 20,
                                /* ppapiName= */ 2,
                                /* times= */ 1));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve throwable for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingErrorCode_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ Any.class,
                                /* errorCode= */ UNDEFINED_INT_PARAM,
                                /* ppapiName= */ 2,
                                /* times= */ 1));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve errorCode for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationMissingPpapiName_throwsException() {
        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ Any.class,
                                /* errorCode= */ 2,
                                /* ppapiName= */ UNDEFINED_INT_PARAM,
                                /* times= */ 1));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Cannot resolve ppapiName for @ExpectErrorLogUtilWithExceptionCall");
    }

    @Test
    public void testGetExpectedLogCalls_withSingleAnnotationAndDefaultParams_returnsNonEmptyList() {
        Description description =
                Description.createTestDescription(
                        AClassWithSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallAnnotation(
                                /* throwable= */ Undefined.class,
                                /* errorCode= */ UNDEFINED_INT_PARAM,
                                /* ppapiName= */ 30,
                                /* times= */ 1));

        ErrorLogUtilCall errorLogUtilCall =
                new ErrorLogUtilCall(
                        /* throwable= */ DEFAULT_THROWABLE,
                        /* errorCode= */ DEFAULT_ERROR_CODE,
                        /* ppapiName= */ 30);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleValidAnnotations_returnsNonEmptyList() {
        ExpectErrorLogUtilWithExceptionCall annotation1 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        ExpectErrorLogUtilWithExceptionCall annotation2 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ Any.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallsAnnotation(
                                new ExpectErrorLogUtilWithExceptionCall[] {
                                    annotation1, annotation2
                                }));

        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(
                        /* throwable= */ Any.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        // Even though both invocations are the same, they are not equal. Therefore,
        // no exception should be thrown calling to de-dupe annotations using times.
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
                .containsExactly(errorLogUtilCall1, errorLogUtilCall2);
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationZeroTimes_throwsException() {
        ExpectErrorLogUtilWithExceptionCall annotation1 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);
        ExpectErrorLogUtilWithExceptionCall annotation2 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ Any.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 0);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallsAnnotation(
                                new ExpectErrorLogUtilWithExceptionCall[] {
                                    annotation1, annotation2
                                }));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "Detected @ExpectErrorLogUtilWithExceptionCall with times = 0. Remove "
                                + "annotation as the test will automatically fail if any log "
                                + "calls are detected.");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationNegativeTimes_throwsException() {
        ExpectErrorLogUtilWithExceptionCall annotation1 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ -1);
        ExpectErrorLogUtilWithExceptionCall annotation2 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ Any.class,
                        /* errorCode= */ 15,
                        /* ppapiName= */ 10,
                        /* times= */ 1);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallsAnnotation(
                                new ExpectErrorLogUtilWithExceptionCall[] {
                                    annotation1, annotation2
                                }));

        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo("Detected @ExpectErrorLogUtilWithExceptionCall with times < 0!");
    }

    @Test
    public void testGetExpectedLogCalls_withMultipleAnnotationsSameInvocation_throwsException() {
        ExpectErrorLogUtilWithExceptionCall annotation1 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 30,
                        /* ppapiName= */ 20,
                        /* times= */ 1);
        ExpectErrorLogUtilWithExceptionCall annotation2 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ IllegalArgumentException.class,
                        /* errorCode= */ 30,
                        /* ppapiName= */ 20,
                        /* times= */ 2);

        Description description =
                Description.createTestDescription(
                        AClassWithNoSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallsAnnotation(
                                new ExpectErrorLogUtilWithExceptionCall[] {
                                    annotation1, annotation2
                                }));

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> mErrorLogUtilVerifier.getExpectedLogCalls(description));

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
        ExpectErrorLogUtilWithExceptionCall annotation1 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ Undefined.class,
                        /* errorCode= */ 30,
                        /* ppapiName= */ 20,
                        /* times= */ 1);
        ExpectErrorLogUtilWithExceptionCall annotation2 =
                newExpectErrorLogUtilWithExceptionCallAnnotation(
                        /* throwable= */ NullPointerException.class,
                        /* errorCode= */ UNDEFINED_INT_PARAM,
                        /* ppapiName= */ 30,
                        /* times= */ 1);

        Description description =
                Description.createTestDescription(
                        AClassWithSetDefaultParamsAnnotation.class,
                        "test",
                        newExpectErrorLogUtilWithExceptionCallsAnnotation(
                                new ExpectErrorLogUtilWithExceptionCall[] {
                                    annotation1, annotation2
                                }));

        ErrorLogUtilCall errorLogUtilCall1 =
                new ErrorLogUtilCall(
                        /* throwable= */ DEFAULT_THROWABLE,
                        /* errorCode= */ 30,
                        /* ppapiName= */ 20,
                        /* times= */ 1);
        ErrorLogUtilCall errorLogUtilCall2 =
                new ErrorLogUtilCall(
                        /* throwable= */ NullPointerException.class,
                        /* errorCode= */ DEFAULT_ERROR_CODE,
                        /* ppapiName= */ 30,
                        /* times= */ 1);
        expect.that(mErrorLogUtilVerifier.getExpectedLogCalls(description))
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

    private static class AClassWithNoSetDefaultParamsAnnotation {}

    @SetErrorLogUtilDefaultParams(
            throwable = Any.class,
            errorCode = DEFAULT_ERROR_CODE,
            ppapiName = DEFAULT_PPAPI)
    private static class AClassWithSetDefaultParamsAnnotation {}

    @AutoAnnotation
    private static ExpectErrorLogUtilWithExceptionCall
            newExpectErrorLogUtilWithExceptionCallAnnotation(
                    Class<? extends Throwable> throwable, int errorCode, int ppapiName, int times) {
        return new AutoAnnotation_AdServicesErrorLogUtilWithExceptionVerifierTest_newExpectErrorLogUtilWithExceptionCallAnnotation(
                throwable, errorCode, ppapiName, times);
    }

    @AutoAnnotation
    private static ExpectErrorLogUtilWithExceptionCalls
            newExpectErrorLogUtilWithExceptionCallsAnnotation(
                    ExpectErrorLogUtilWithExceptionCall[] value) {
        return new AutoAnnotation_AdServicesErrorLogUtilWithExceptionVerifierTest_newExpectErrorLogUtilWithExceptionCallsAnnotation(
                value);
    }
}
