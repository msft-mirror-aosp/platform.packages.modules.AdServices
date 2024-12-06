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

import static com.android.adservices.common.logging.ErrorLogUtilCall.None;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall.ANNOTATION_NAME;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.mockito.ArgumentMatchers.anyInt;

import android.util.Log;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCalls;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.errorlogging.ErrorLogUtil;
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
 * Log verifier for scanning usage of {@code ErrorLogUtil.e(int, int)} invocations. Use {@link
 * ExpectErrorLogUtilCall} to verify logging calls.
 */
public final class AdServicesErrorLogUtilVerifier extends AbstractLogVerifier<ErrorLogUtilCall> {
    @Override
    protected void mockLogCalls() {
        // Mock ErrorLogUtil.e(int, int) calls and capture logging arguments.
        doAnswer(
                        invocation -> {
                            recordActualCall(
                                    new ErrorLogUtilCall(
                                            None.class,
                                            invocation.getArgument(0),
                                            invocation.getArgument(1)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
    }

    @Override
    public Set<ErrorLogUtilCall> getExpectedLogCalls(Description description) {
        List<ExpectErrorLogUtilCall> annotations = getAnnotations(description);
        SetErrorLogUtilDefaultParams defaultParams =
                TestHelper.getAnnotationFromAnywhere(
                        description, SetErrorLogUtilDefaultParams.class);

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

    @Override
    public String getResolutionMessage() {
        return "Please make sure to use @"
                + ANNOTATION_NAME
                + "(..) "
                + "over test method to denote "
                + "all expected ErrorLogUtil.e(int, int) calls.";
    }

    private List<ExpectErrorLogUtilCall> getAnnotations(Description description) {
        // Scan for multiple annotation container
        ExpectErrorLogUtilCalls multiple = description.getAnnotation(ExpectErrorLogUtilCalls.class);
        if (multiple != null) {
            return Arrays.stream(multiple.value()).collect(Collectors.toList());
        }

        // Scan for single annotation
        ExpectErrorLogUtilCall single = description.getAnnotation(ExpectErrorLogUtilCall.class);
        return single == null ? ImmutableList.of() : ImmutableList.of(single);
    }
}
