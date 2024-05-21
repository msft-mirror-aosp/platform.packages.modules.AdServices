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
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.shared.testing.AbstractLogVerifier;

import org.junit.runner.Description;

import java.util.HashSet;
import java.util.Set;

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
        // TODO(b/337042949): Support repeatable annotations
        ExpectErrorLogUtilCall annotation = description.getAnnotation(ExpectErrorLogUtilCall.class);

        Set<ErrorLogUtilCall> expectedCalls = new HashSet<>();
        if (annotation == null) {
            Log.v(mTag, "No @ExpectErrorLogUtilCall found over test method.");
            return expectedCalls;
        }

        validateAnnotation(annotation);

        expectedCalls.add(
                new ErrorLogUtilCall(
                        annotation.throwable(),
                        annotation.errorCode(),
                        annotation.ppapiName(),
                        annotation.times()));

        return expectedCalls;
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
