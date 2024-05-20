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
package com.android.adservices.shared.testing.concurrency;

import com.android.adservices.shared.testing.AndroidLogger;

/** Base class for device-side sync callbacks for testing. */
public abstract class AbstractTestSyncCallback extends AbstractSidelessTestSyncCallback {

    // NOTE: currently there's no usage that takes a custom timeout, but we could add on demand

    protected AbstractTestSyncCallback() {
        this(/* expectedNumberOfCalls= */ 1);
    }

    protected AbstractTestSyncCallback(int expectedNumberOfCalls) {
        super(AndroidLogger.getInstance(), expectedNumberOfCalls);
    }
}
