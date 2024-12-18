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

public final class FailableOnResultSyncCallbackTest
        extends OnFailableResultSyncCallbackTestCase<
                String, Exception, FailableOnResultSyncCallback<String, Exception>> {

    @Override
    protected FailableOnResultSyncCallback<String, Exception> newCallback(
            SyncCallbackSettings settings) {
        return new ConcreteFailableOnResultSyncCallback(settings);
    }

    @Override
    protected String newResult() {
        return "It's not a failure, it's a feature-" + getNextUniqueId();
    }

    @Override
    protected Exception newFailure() {
        return new Exception("D'OH: " + getNextUniqueId() + "!");
    }

    private static final class ConcreteFailableOnResultSyncCallback
            extends FailableOnResultSyncCallback<String, Exception> {

        @SuppressWarnings("unused") // Called by superclass using reflection
        ConcreteFailableOnResultSyncCallback() {
            super();
        }

        ConcreteFailableOnResultSyncCallback(SyncCallbackSettings settings) {
            super(settings);
        }
    }
}
