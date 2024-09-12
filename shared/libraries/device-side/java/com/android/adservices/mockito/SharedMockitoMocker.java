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

/** Implements {@link SharedMocker} using {@code Mockito}. */
public final class SharedMockitoMocker extends AbstractMocker implements SharedMocker {

    @Override
    public Context setApplicationContextSingleton() {
        Context context = mock(Context.class);
        logV("setApplicationContextSingleton(): will use %s as appContext", context);

        when(context.getApplicationContext()).thenReturn(context);
        ApplicationContextSingleton.setAsIs(context);

        return context;
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
}
