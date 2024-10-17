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
package com.android.adservices.shared.testing;

import com.google.errorprone.annotations.concurrent.GuardedBy;

import java.util.Objects;

/**
 * Base class for actions.
 *
 * <p>It doesn't do much, other than providing a logger and making sure the lifecycle methods are
 * properly called...
 */
public abstract class AbstractAction implements Action {

    protected final Logger mLog;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mExecuted;

    @GuardedBy("mLock")
    private boolean mExecuteResult;

    @GuardedBy("mLock")
    private boolean mReverted;

    protected AbstractAction(Logger logger) {
        mLog = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public final boolean execute() throws Exception {
        synchronized (mLock) {
            if (mExecuted) {
                throw new IllegalStateException(this + " already executed");
            }
            mExecuted = true;
            mExecuteResult = onExecuteLocked();
            return mExecuteResult;
        }
    }

    @Override
    public final boolean isExecuted() {
        synchronized (mLock) {
            return mExecuted;
        }
    }

    /**
     * Effective {@code execute()} method.
     *
     * <p>This is called by {@link #execute()} when previous checks passed.
     */
    @GuardedBy("mLock")
    protected abstract boolean onExecuteLocked() throws Exception;

    @Override
    public final void revert() throws Exception {
        synchronized (mLock) {
            // rename / guard
            assertExecutedLocked();
            if (!mExecuteResult) {
                mLog.v("Not calling revert() when execute() returned false");
                return;
            }
            if (mReverted) {
                throw new IllegalStateException(this + " already reverted");
            }
            mReverted = true;
            onRevertLocked();
        }
    }

    @Override
    public boolean isReverted() {
        synchronized (mLock) {
            return mReverted;
        }
    }

    /**
     * Effective {@code revert()} method.
     *
     * <p>This is called by {@link #revert()} when previous checks passed; for example, it's not
     * called if {@link #onExecuteLocked()} returned {@code false}.
     */
    @GuardedBy("mLock")
    protected abstract void onRevertLocked() throws Exception;

    @Override
    public final void reset() {
        synchronized (mLock) {
            if (mExecuted) {
                assertRevertedLocked();
            }
            mExecuteResult = false;
            mExecuted = false;
            mReverted = false;
            onResetLocked();
        }
    }

    /**
     * Effective {@code reset()} method.
     *
     * <p>This is called by {@link #reset()} when previous checks passed; for example, it's not
     * called if {@link #onExecuteLocked()} was not called yet.
     */
    @GuardedBy("mLock")
    protected abstract void onResetLocked();

    @GuardedBy("mLock")
    private void assertExecutedLocked() {
        if (!mExecuted) {
            throw new IllegalStateException("Not executed yet");
        }
    }

    @GuardedBy("mLock")
    private void assertRevertedLocked() {
        if (!mReverted) {
            throw new IllegalStateException("Not reverted yet");
        }
    }
}
