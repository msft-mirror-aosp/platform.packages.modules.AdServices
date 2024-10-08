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

import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.flags.Action;

import java.util.Objects;

/** Fake action! */
public final class FakeAction implements Action {

    private boolean mExecuted;
    private boolean mOnExecute;
    private boolean mReverted;

    @Nullable private Exception mOnExecuteException;
    @Nullable private Exception mOnRevertException;

    @Override
    public boolean execute() throws Exception {
        mExecuted = true;
        if (mOnExecuteException != null) {
            throw mOnExecuteException;
        }
        return mOnExecute;
    }

    @Override
    public void revert() throws Exception {
        mReverted = true;
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

    /** Sets an exception to be thrown by {@link #revert()}. */
    public void onRevertThrows(Exception exception) {
        mOnRevertException = Objects.requireNonNull(exception, "exception cannot be null");
    }

    /** Checks whether {@link #revert()} was called. */
    public boolean reverted() {
        return mReverted;
    }

    @Override
    public String toString() {
        return "FakeAction[mExecuted="
                + mExecuted
                + ", mReverted="
                + mReverted
                + ", mOnExecute="
                + mOnExecute
                + ", mOnExecuteException="
                + mOnExecuteException
                + ", mOnRevertException="
                + mOnRevertException
                + "]";
    }
}
