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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import android.util.Log;

import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCalls;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.shared.testing.AbstractLogVerifier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.runner.Description;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Log verifier for {@link ErrorLogUtil} calls. */
public final class AdServicesErrorLogUtilVerifier extends AbstractLogVerifier<ErrorLogUtilCall> {
    @Override
    protected void mockLogCalls() {
        // Mock ErrorLogUtil.e(int, int) calls and capture logging arguments.
        doAnswer(
                        invocation -> {
                            recordActualCall(
                                    new ErrorLogUtilCall(
                                            ExpectErrorLogUtilCall.None.class,
                                            invocation.getArgument(0),
                                            invocation.getArgument(1)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(anyInt(), anyInt()));

        // Mock ErrorLogUtil.e(Throwable, int, int) calls and capture logging arguments.
        doAnswer(
                        invocation -> {
                            recordActualCall(
                                    new ErrorLogUtilCall(
                                            ((Throwable) invocation.getArgument(0)).getClass(),
                                            invocation.getArgument(1),
                                            invocation.getArgument(2)));
                            return null;
                        })
                .when(() -> ErrorLogUtil.e(any(Throwable.class), anyInt(), anyInt()));
    }

    @Override
    public Set<ErrorLogUtilCall> getExpectedLogCalls(Description description) {
        List<ExpectErrorLogUtilCall> annotations = getAnnotations(description);

        if (annotations.isEmpty()) {
            Log.v(mTag, "No @ExpectErrorLogUtilCall found over test method.");
            return ImmutableSet.of();
        }

        List<ErrorLogUtilCall> expectedCalls =
                annotations.stream()
                        .peek(this::validateAnnotation)
                        .map(
                                annotation ->
                                        new ErrorLogUtilCall(
                                                annotation.throwable(),
                                                annotation.errorCode(),
                                                annotation.ppapiName(),
                                                annotation.times()))
                        .collect(Collectors.toList());

        // Need to compare without taking into account times arg i.e. use invocation equality.
        if (!containsUniqueLogInvocations(expectedCalls)) {
            throw new IllegalStateException(
                    "Detected @ExpectErrorLogUtilCall annotations representing the same "
                            + "invocation! De-dupe by using times arg");
        }

        return new HashSet<>(expectedCalls);
    }

    @Override
    public String getResolutionMessage() {
        // TODO (b/337043102): Update message to include info about default args
        return "Please make sure to use @ExpectErrorLogUtilCall(..) over test method to denote "
                + "all expected ErrorLogUtil.e(..) calls.";
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

    private void validateAnnotation(ExpectErrorLogUtilCall annotation) {
        int times = annotation.times();

        if (times == 0) {
            throw new IllegalStateException(
                    "Detected @ExpectErrorLogUtilCall with times = 0. Remove annotation as the "
                            + "test will automatically fail if any log calls are detected.");
        }
        if (times < 0) {
            throw new IllegalStateException("Detected @ExpectErrorLogUtilCall with times < 0!");
        }
    }
}
