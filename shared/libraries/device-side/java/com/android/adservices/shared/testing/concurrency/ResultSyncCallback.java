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

import com.android.adservices.shared.testing.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code SyncCallback} use to return an object (result).
 *
 * @param <R> type of the result.
 */
public class ResultSyncCallback<R> extends DeviceSideSyncCallback
        implements IResultSyncCallback<R> {

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private R mResult;

    @GuardedBy("mLock")
    private final List<R> mResults = new ArrayList<>();

    public ResultSyncCallback() {
        super(SyncCallbackFactory.newSettingsBuilder().build());
    }

    public ResultSyncCallback(SyncCallbackSettings settings) {
        super(settings);
    }

    @VisibleForTesting
    ResultSyncCallback(AbstractSyncCallback realCallback, SyncCallbackSettings settings) {
        super(realCallback, settings);
    }

    @Override
    public final void injectResult(@Nullable R result) {
        internalInjectResult("injectResult", result);
    }

    // TODO(b/342448771): Make this method package protected
    protected final void internalInjectResult(String name, @Nullable R result) {
        StringBuilder methodName = new StringBuilder(name).append("(").append(result);
        synchronized (mLock) {
            boolean firstCall = mResults.isEmpty();
            if (firstCall) {
                mResult = result;
            } else {
                // Don't set mResult
                methodName
                        .append("; already called: mResult=")
                        .append(mResult)
                        .append(", mResults=")
                        .append(mResults);
            }
            mResults.add(result);
        }
        super.internalSetCalled(methodName.append(')').toString());
    }

    @Override
    public @Nullable R assertResultReceived() throws InterruptedException {
        super.assertCalled();
        return getResult();
    }

    @Override
    public final @Nullable R getResult() {
        synchronized (mLock) {
            return mResult;
        }
    }

    @Override
    public List<R> getResults() {
        synchronized (mLock) {
            return mResults.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(mResults));
        }
    }

    @Override
    protected void customizeToString(StringBuilder string) {
        super.customizeToString(string);

        synchronized (mLock) {
            List<R> results = getResults();
            if (results.isEmpty()) {
                string.append(", (no result yet)");
            } else {
                string.append(", result=").append(getResult()).append(", results=").append(results);
            }
        }
    }

    // Ideally should be moved to some helper class (which would require unit-testint it as well)
    static <I> List<I> getImmutableList(List<I> list) {
        return list.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
    }
}
