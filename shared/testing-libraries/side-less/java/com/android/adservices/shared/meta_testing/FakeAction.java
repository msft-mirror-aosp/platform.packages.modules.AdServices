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
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
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
    @Nullable private RuntimeException mOnResetException;
    @Nullable private Boolean mOnExecute;

    private final AtomicInteger mNumberTimesExecuteCalled = new AtomicInteger();
    private final Logger mLog = new Logger(DynamicLogger.getInstance(), FakeAction.class);

    private boolean mReverted;
    private boolean mThrowIfExecuteCalledMultipleTimes;
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
    public void reset() {
        if (mOnResetException != null) {
            throw mOnResetException;
        }
        mNumberTimesExecuteCalled.set(0);
        mReverted = false;
        mExecutionOrder = 0;
        mReversionOrder = 0;
    }

    @Override
    public boolean execute() throws Exception {
        int callNumber = mNumberTimesExecuteCalled.incrementAndGet();
        if (mThrowIfExecuteCalledMultipleTimes && callNumber > 1) {
            throw new IllegalArgumentException(
                    "Should be called just once, but this is call #" + callNumber);
        }
        if (mExecutionOrderCounter != null) {
            mExecutionOrder = mExecutionOrderCounter.incrementAndGet();
        }
        mLog.v(
                "execute(): call #%d, mExecutionOrder=%d, mOnExecuteException=%s",
                callNumber, mExecutionOrder, mOnExecuteException);
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
        mLog.v(
                "revert(): mReversionOrder=%d, mOnRevertException=%s",
                mReversionOrder, mOnRevertException);
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

    /**
     * When called, {@link #execute()} will throw if called again (without calling {@link
     * #reset()}).
     */
    public void throwIfExecuteCalledMultipleTime() {
        mThrowIfExecuteCalledMultipleTimes = true;
    }

    @Override
    public boolean isExecuted() {
        return mNumberTimesExecuteCalled.get() > 0;
    }

    /** Returns how many times {@link #execute()} was called. */
    public int getNumberTimesExecuteCalled() {
        return mNumberTimesExecuteCalled.get();
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

    @Override
    public boolean isReverted() {
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

    /** Sets an exception to be thrown by {@link #reset()}. */
    public void onResetThrows(RuntimeException exception) {
        mOnResetException = Objects.requireNonNull(exception, "exception cannot be null");
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder("FakeAction[");
        if (mName != null) {
            string.append("name=").append(mName).append(", ");
        }
        string.append("mExecuted=")
                .append(isExecuted())
                .append(", mNumberTimesExecuteCalled=")
                .append(mNumberTimesExecuteCalled.get())
                .append(", mReverted=")
                .append(mReverted)
                .append(", mOnExecute=")
                .append(mOnExecute)
                .append(",  mThrowIfExecuteCalledMultipleTimes")
                .append(mThrowIfExecuteCalledMultipleTimes);
        if (mExecutionOrderCounter != null) {
            string.append(", mExecutionOrder=")
                    .append(mExecutionOrder)
                    .append(", mReversionOrder=")
                    .append(mReversionOrder);
        }
        if (mOnExecuteException != null) {
            string.append(", mOnExecuteException=").append(mOnExecuteException);
        }
        if (mOnRevertException != null) {
            string.append(", mOnRevertException=").append(mOnRevertException);
        }
        if (mOnResetException != null) {
            string.append(", mOnResetException=").append(mOnResetException);
        }
        return string.append(']').toString();
    }
}
