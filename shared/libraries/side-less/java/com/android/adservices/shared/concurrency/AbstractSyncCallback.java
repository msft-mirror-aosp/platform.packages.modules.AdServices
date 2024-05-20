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
package com.android.adservices.shared.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Base implementation for {@link SyncCallback}. */
public abstract class AbstractSyncCallback implements SyncCallback {

    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    private final int mId = sIdGenerator.incrementAndGet();
    private final int mNumberExpectedCalls;
    private final CountDownLatch mLatch;

    /** Default constructor (for callbacks that should be called just once). */
    public AbstractSyncCallback() {
        this(1);
    }

    /** Constructor for callbacks that should be called multiple times. */
    public AbstractSyncCallback(int numberOfExpectedCalls) {
        mNumberExpectedCalls = numberOfExpectedCalls;
        mLatch = new CountDownLatch(mNumberExpectedCalls);
    }

    /**
     * By default is a no-op, but subclasses could override to add additional info to {@code
     * toString()}.
     */
    protected void customizeToString(StringBuilder string) {}

    @Override
    public final void setCalled() {
        try {
            mLatch.countDown();
        } finally {
            logSetCalled();
        }
    }

    @Override
    public final void waitCalled() throws InterruptedException {
        try {
            mLatch.await();
        } finally {
            logWaitCalled();
        }
    }

    @Override
    public final void waitCalled(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            if (!mLatch.await(timeout, unit)) {
                throw new IllegalStateException(this + " not called in " + timeout + " " + unit);
            }
        } finally {
            logWaitCalled(timeout, unit);
        }
    }

    @Override
    public final boolean isCalled() {
        return mLatch.getCount() == 0;
    }

    @Override
    public final String toString() {
        StringBuilder string =
                new StringBuilder()
                        .append('[')
                        .append(getClass().getSimpleName())
                        .append(": id=")
                        .append(mId)
                        .append(", numberExpectedCalls=")
                        .append(mNumberExpectedCalls)
                        .append(", missingCalls=")
                        .append(mLatch.getCount());
        customizeToString(string);
        return string.append(']').toString();
    }

    // TODO(b/337014024): create proper log methods (or document) methods below

    protected void logSetCalled() {
        System.err.printf("logSetCalled() (and not overridden by %s)\n", this);
    }

    protected void logWaitCalled() throws InterruptedException {
        System.err.printf("logWaitCalled() (and not overridden by %s)\n", this);
    }

    protected void logWaitCalled(long timeout, TimeUnit unit) throws InterruptedException {
        System.err.printf(
                "logWaitCalled(%d, %s) (and not overridden by %s)\n", timeout, unit, this);
    }
}
