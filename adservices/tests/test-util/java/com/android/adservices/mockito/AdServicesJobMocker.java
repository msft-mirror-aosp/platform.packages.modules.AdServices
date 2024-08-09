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

import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;

/**
 * Helper interface providing common expectations for (Android) job-related functionalities that are
 * specific to AdServices.
 */
public interface AdServicesJobMocker {

    /**
     * Mocks a call to {@link AdServicesJobServiceFactory#getJobSchedulingLogger()}.
     *
     * @return a mocked instance of {@link JobSchedulingLogger} that will be returned by that call.
     */
    JobSchedulingLogger mockJobSchedulingLogger(AdServicesJobServiceFactory factory);

    /** Gets a spied instance of {@link AdServicesJobServiceLogger}. */
    AdServicesJobServiceLogger getSpiedAdServicesJobServiceLogger(Context context, Flags flags);
}
