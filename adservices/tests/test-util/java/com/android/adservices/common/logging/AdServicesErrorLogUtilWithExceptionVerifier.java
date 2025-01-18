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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.ANNOTATION_NAME;

import android.util.Log;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCalls;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.shared.testing.AbstractLogVerifier;
import com.android.adservices.shared.testing.TestHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.runner.Description;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Log verifier for scanning usage of {@code ErrorLogUtil.e(Throwable, int, int)} invocations. Use
 * {@link ExpectErrorLogUtilWithExceptionCall} to verify both background and non-background logging
 * calls.
 */
public final class AdServicesErrorLogUtilWithExceptionVerifier
        extends AbstractLogVerifier<ErrorLogUtilCall> {
    private ErrorLogUtilSyncCallback mErrorLogUtilSyncCallback;
    private Set<ErrorLogUtilCall> mExpectedLogCallsCache;

    @Override
    protected void mockLogCalls(Description description) {
        // Configure the sync callback to await for expected number of calls to be made before
        // verification. Note: default timeout is set to 5 seconds before the test fails.
        mErrorLogUtilSyncCallback =
                ErrorLogUtilSyncCallback.mockErrorLogUtilWithThrowable(
                        getTotalExpectedLogCalls(description));
    }

    @Override
    public Set<ErrorLogUtilCall> getExpectedLogCalls(Description description) {
        if (mExpectedLogCallsCache == null) {
            // Parsing the expected calls is required at least twice: once for configuring the
            // number of calls for the sync callback and once in the verification step after
            // test execution. Expected log calls are cached so it's parsed only once.
            mExpectedLogCallsCache = parseExpectedLogCalls(description);
        }
        return mExpectedLogCallsCache;
    }

    @Override
    public Set<ErrorLogUtilCall> getActualLogCalls() {
        if (mErrorLogUtilSyncCallback == null) {
            throw new IllegalStateException(
                    "mErrorLogUtilSyncCallback is null, which indicates mocking isn't done prior "
                            + "to fetching the actual log calls.");
        }

        return dedupeCalls(mErrorLogUtilSyncCallback.getResultsReceivedUponWaiting());
    }

    @Override
    public String getResolutionMessage() {
        return "Please make sure to use @"
                + ANNOTATION_NAME
                + "(..) "
                + "over test method to denote "
                + "all expected ErrorLogUtil.e(Throwable, int, int) calls.";
    }

    private Set<ErrorLogUtilCall> parseExpectedLogCalls(Description description) {
        List<ExpectErrorLogUtilWithExceptionCall> annotations = getAnnotations(description);
        SetErrorLogUtilDefaultParams defaultParams =
                TestHelper.getAnnotationFromTypesOnly(
                        description.getTestClass(), SetErrorLogUtilDefaultParams.class);

        if (annotations.isEmpty()) {
            Log.v(mTag, "No @" + ANNOTATION_NAME + " found over test method.");
            return ImmutableSet.of();
        }

        Set<ErrorLogUtilCall> expectedCalls =
                annotations.stream()
                        .peek(a -> validateTimes(a.times(), ANNOTATION_NAME))
                        .map(a -> ErrorLogUtilCall.createFrom(a, defaultParams))
                        .collect(Collectors.toSet());

        if (expectedCalls.size() != annotations.size()) {
            throw new IllegalStateException(
                    "Detected @"
                            + ANNOTATION_NAME
                            + " annotations representing the same "
                            + "invocation! De-dupe by using times arg");
        }

        return expectedCalls;
    }

    private List<ExpectErrorLogUtilWithExceptionCall> getAnnotations(Description description) {
        // Scan for multiple annotation container
        ExpectErrorLogUtilWithExceptionCalls multiple =
                description.getAnnotation(ExpectErrorLogUtilWithExceptionCalls.class);
        if (multiple != null) {
            return Arrays.stream(multiple.value()).collect(Collectors.toList());
        }

        // Scan for single annotation
        ExpectErrorLogUtilWithExceptionCall single =
                description.getAnnotation(ExpectErrorLogUtilWithExceptionCall.class);
        return single == null ? ImmutableList.of() : ImmutableList.of(single);
    }

    private int getTotalExpectedLogCalls(Description description) {
        return getExpectedLogCalls(description).stream().mapToInt(call -> call.mTimes).sum();
    }
}
