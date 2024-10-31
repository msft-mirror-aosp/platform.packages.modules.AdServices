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
package com.android.adservices.mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.util.Clock;

import org.mockito.stubbing.OngoingStubbing;

import java.util.Arrays;
import java.util.Objects;

/** Implements {@link SharedMocker} using {@code Mockito}. */
public final class SharedMockitoMocker extends AbstractMocker implements SharedMocker {

    @Override
    public Context setApplicationContextSingleton() {
        Context context = mock(Context.class);
        logV("setApplicationContextSingleton(): will use %s as appContext", context);

        when(context.getApplicationContext()).thenReturn(context);
        ApplicationContextSingleton.setForTests(context);

        return context;
    }

    @Override
    public void mockSetApplicationContextSingleton(Context context) {
        Objects.requireNonNull(context, "context cannot be null");
        logV("mockSetApplicationContextSingleton(%s)", context);

        ApplicationContextSingleton.setForTests(context);
    }

    @Override
    public JobServiceLoggingCallback syncRecordOnStopJob(JobServiceLogger logger) {
        JobServiceLoggingCallback callback = new JobServiceLoggingCallback();

        doAnswer(
                        invocation -> {
                            logV("calling callback %s on %s", callback, invocation);
                            callback.onLoggingMethodCalled();
                            return null;
                        })
                .when(logger)
                .recordOnStopJob(any(), anyInt(), anyBoolean());

        return callback;
    }

    @Override
    public void mockCurrentTimeMillis(Clock mockClock, long... mockedValues) {
        logV("mockCurrentTimeMillis(%s, %s)", mockClock, Arrays.toString(mockedValues));
        Objects.requireNonNull(mockClock, "mockClock cannot be null");
        Objects.requireNonNull(mockedValues, "mockedValues cannot be null");

        unflatLongVararg(when(mockClock.currentTimeMillis()), mockedValues);
    }

    @Override
    public void mockElapsedRealtime(Clock mockClock, long... mockedValues) {
        logV("mockElapsedRealtime(%s, %s)", mockClock, Arrays.toString(mockedValues));
        Objects.requireNonNull(mockClock, "mockClock cannot be null");
        Objects.requireNonNull(mockedValues, "mockedValues cannot be null");

        unflatLongVararg(when(mockClock.elapsedRealtime()), mockedValues);
    }

    // TODO(b/359358687): Move to MockitoHelper / rename / unit test it

    // Mockito's when() supporting passing a single value or a single value plus a vararg, but the
    // signature of those methods take a T (object), so they cannot be used with primitive types.
    // Hence, this method provides a "bridge" between helper methods that take long... and Mockito.
    private static void unflatLongVararg(OngoingStubbing<Long> mockedMethod, long... mockedValues) {
        Long firstValue = mockedValues[0];
        if (mockedValues.length == 1) {
            mockedMethod.thenReturn(firstValue);
            return;
        }

        int remainingLength = mockedValues.length - 1;
        Long[] remainingValues = new Long[remainingLength];
        for (int i = 0; i < remainingLength; i++) {
            remainingValues[i] = mockedValues[i + 1];
        }
        mockedMethod.thenReturn(firstValue, remainingValues);
    }
}
