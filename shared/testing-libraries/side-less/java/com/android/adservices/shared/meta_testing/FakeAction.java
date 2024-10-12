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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.Action;
import com.android.adservices.shared.testing.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake action! */
public final class FakeAction implements Action {

    @Nullable private final String mName;
    @Nullable private final AtomicInteger mExecutionOrderCounter;
    @Nullable private final AtomicInteger mReversionOrderCounter;
    @Nullable private Exception mOnExecuteException;
    @Nullable private Exception mOnRevertException;
    @Nullable private Boolean mOnExecute;

    private boolean mExecuted;
    private boolean mReverted;
    private int mExecutionOrder;
    private int mReversionOrder;

    /** Default constructor, don't set anything. */
    public FakeAction() {
        this(
                /* checkNull= */ false,
                /* name= */ null,
                /* executionOrderCounter= */ null,
                /* reversionOrderCounter= */ null);
    }

    /**
     * Constructor used when tests need to check the order of multiple actions.
     *
     * @param name name of the object (used on {@link #toString()}
     * @param executionOrderCounter counter that is incremented when {@link #execute()} is called.
     * @param reversionOrderCounter counter that is incremented when {@link #revert()} is called.
     */
    public FakeAction(
            String name, AtomicInteger executionOrderCounter, AtomicInteger reversionOrderCounter) {
        this(/* checkNull= */ true, name, executionOrderCounter, reversionOrderCounter);
    }

    private FakeAction(
            boolean checkNull,
            String name,
            AtomicInteger executionOrderCounter,
            AtomicInteger reversionOrderCounter) {
        if (checkNull) {
            Objects.requireNonNull(name, "mName cannot be null");
            Objects.requireNonNull(executionOrderCounter, "executionOrderCounter cannot be null");
            Objects.requireNonNull(reversionOrderCounter, "reversionOrderCounter cannot be null");
        }
        mName = name;
        mExecutionOrderCounter = executionOrderCounter;
        mReversionOrderCounter = reversionOrderCounter;
    }

    @Override
    public boolean execute() throws Exception {
        mExecuted = true;
        if (mExecutionOrderCounter != null) {
            mExecutionOrder = mExecutionOrderCounter.incrementAndGet();
        }
        if (mOnExecuteException != null) {
            throw mOnExecuteException;
        }
        return mOnExecute == null || mOnExecute;
    }

    @Override
    public void revert() throws Exception {
        mReverted = true;
        if (mReversionOrderCounter != null) {
            mReversionOrder = mReversionOrderCounter.incrementAndGet();
        }
        if (mOnRevertException != null) {
            throw mOnRevertException;
        }
    }

    /** Sets the returned value of {@link #execute()}. */
    public void onExecuteReturn(boolean value) {
        mOnExecute = value;
    }

    /** Sets an exception to be thrown by {@link #execute()}. */
    public void onExecuteThrows(Exception exception) {
        mOnExecuteException = Objects.requireNonNull(exception, "exception cannot be null");
    }

    /** Checks whether {@link #execute()} was called. */
    public boolean executed() {
        return mExecuted;
    }

    /**
     * Gets the execution order of this action.
     *
     * @throws IllegalStateException if it was not constructed using {@link #FakeAction(String,
     *     AtomicInteger, AtomicInteger)}.
     */
    public int getExecutionOrder() {
        if (mExecutionOrderCounter == null) {
            throw new IllegalStateException("Not created with executionOrder consttructor");
        }
        return mExecutionOrder;
    }

    /** Sets an exception to be thrown by {@link #revert()}. */
    public void onRevertThrows(Exception exception) {
        mOnRevertException = Objects.requireNonNull(exception, "exception cannot be null");
    }

    /** Checks whether {@link #revert()} was called. */
    public boolean reverted() {
        return mReverted;
    }

    /**
     * Gets the reversion order of this action.
     *
     * @throws IllegalStateException if it was not constructed using {@link #FakeAction(String,
     *     AtomicInteger, AtomicInteger)}.
     */
    public int getReversionOrder() {
        if (mReversionOrderCounter == null) {
            throw new IllegalStateException("Not created with reversionOrder consttructor");
        }
        return mReversionOrder;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder("FakeAction[");
        if (mName != null) {
            string.append("name=").append(mName).append(", ");
        }
        string.append("mExecuted=")
                .append(mExecuted)
                .append(", mReverted=")
                .append(mReverted)
                .append(", mOnExecute=")
                .append(mOnExecute);
        if (mExecutionOrderCounter != null) {
            string.append(", mExecutionOrder=")
                    .append(mExecutionOrder)
                    .append(", mReversionOrder=")
                    .append(mReversionOrder);
        }
        if (mOnExecuteException != null) {
            string.append(", mOnExecuteException=").append(mOnExecuteException);
        }
        return string.append(']').toString();
    }
}
