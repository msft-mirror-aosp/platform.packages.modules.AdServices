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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for actions.
 *
 * <p>It doesn't do much, other than providing a logger and making sure the lifecycle methods are
 * properly called...
 */
public abstract class AbstractAction implements Action {

    protected final Logger mLog;

    private final AtomicBoolean mExecuted = new AtomicBoolean();
    private final AtomicBoolean mExecuteResult = new AtomicBoolean();
    private final AtomicBoolean mReverted = new AtomicBoolean();

    protected AbstractAction(Logger logger) {
        mLog = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public final boolean execute() throws Exception {
        if (mExecuted.getAndSet(true)) {
            throw new IllegalStateException(this + " already executed");
        }

        boolean result = onExecute();
        mExecuteResult.set(result);

        return result;
    }

    /**
     * Effective {@code execute()} method.
     *
     * <p>This is called by {@link #execute()} when previous checks passed.
     */
    protected abstract boolean onExecute() throws Exception;

    @Override
    public final void revert() throws Exception {
        if (!mExecuted.get()) {
            throw new IllegalStateException("Not executed yet");
        }
        if (!mExecuteResult.get()) {
            mLog.v("Not calling revert() when execute() returned false");
            return;
        }
        if (mReverted.getAndSet(true)) {
            throw new IllegalStateException(this + " already reverted");
        }
        onRevert();
    }

    /**
     * Effective {@code revert()} method.
     *
     * <p>This is called by {@link #revert()} when previous checks passed; for example, it's not
     * called if {@link #onExecute()} returned {@code false}.
     */
    protected abstract void onRevert() throws Exception;
}
