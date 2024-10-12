/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.shared.testing;

import android.app.job.JobService;

import com.android.adservices.shared.testing.concurrency.DeviceSideSyncCallback;
import com.android.adservices.shared.testing.concurrency.SyncCallbackFactory;
import com.android.adservices.shared.testing.concurrency.SyncCallbackSettings;

// TODO(b/344610522): add unit test
/**
 * A synchronized callback used for logging {@link JobService} on testing purpose.
 *
 * <p>The logging methods in {@link AdServicesJobServiceLogger} are offloaded to a separate thread.
 * In order to make the test result deterministic, use this callback to help wait for the completion
 * of such logging methods.
 */
public final class JobServiceLoggingCallback extends DeviceSideSyncCallback {

    public JobServiceLoggingCallback() {
        this(SyncCallbackFactory.newDefaultSettings());
    }

    public JobServiceLoggingCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    /** This is used for checking a stub method is called. */
    public void onLoggingMethodCalled() {
        internalSetCalled("onLoggingMethodCalled()");
    }

    /** Assert the corresponding logging method has happened. */
    public void assertLoggingFinished() throws InterruptedException {
        assertCalled();
    }
}
