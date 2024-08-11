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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.util.Clock;
import com.android.adservices.spe.AdServicesJobInfo;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.adservices.spe.AdServicesStatsdJobServiceLogger;

import java.util.Objects;
import java.util.concurrent.Executors;

/** {@link AdServicesJobMocker} implementation that uses {@code Mockito}. */
public final class AdServicesMockitoJobMocker extends AbstractMocker
        implements AdServicesJobMocker {

    @Override
    @SuppressWarnings("NewApi") // TODO(b/357944639): remove
    public JobSchedulingLogger mockJobSchedulingLogger(AdServicesJobServiceFactory factory) {
        logV("mockJobSchedulingLogger(%s)", factory);
        Objects.requireNonNull(factory, "AdServicesJobServiceFactory cannot be null");

        JobSchedulingLogger loggerMock = mock(JobSchedulingLogger.class);
        when(factory.getJobSchedulingLogger()).thenReturn(loggerMock);

        return loggerMock;
    }

    @Override
    public AdServicesJobServiceLogger getSpiedAdServicesJobServiceLogger(
            Context context, Flags flags) {
        nonNull(context);
        nonNull(flags);

        return spy(
                new AdServicesJobServiceLogger(
                        context,
                        Clock.getInstance(),
                        mock(AdServicesStatsdJobServiceLogger.class),
                        mock(AdServicesErrorLogger.class),
                        Executors.newCachedThreadPool(),
                        AdServicesJobInfo.getJobIdToJobNameMap(),
                        flags));
    }

    private static Context nonNull(Context context) {
        return Objects.requireNonNull(context, "Context cannot be null");
    }

    private static Flags nonNull(Flags flags) {
        return Objects.requireNonNull(flags, "Flags cannot be null");
    }
}
