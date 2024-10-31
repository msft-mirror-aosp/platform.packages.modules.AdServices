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

import static com.android.adservices.shared.testing.SdkSandbox.State.UNKNOWN;

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.SdkSandbox;

/** Fake SdkSandbox! */
public final class FakeSdkSandbox implements SdkSandbox {

    @Nullable private State mState = UNKNOWN;
    @Nullable private RuntimeException mOnGetStateException;
    @Nullable private RuntimeException mOnSetStateException;

    private final Logger mLog;

    /** Default constructor, sets logger. */
    public FakeSdkSandbox() {
        this(DynamicLogger.getInstance());
    }

    /** Constructor with a custom logger. */
    public FakeSdkSandbox(RealLogger realLogger) {
        mLog = new Logger(realLogger, getClass());
    }

    @Override
    public State getState() {
        if (mOnGetStateException != null) {
            mLog.i(
                    "getState(): throwing exception set by onGetStateThrows(): %s",
                    mOnGetStateException);
            throw mOnGetStateException;
        }
        return mState;
    }

    @Override
    public FakeSdkSandbox setState(@Nullable State state) {
        if (mOnSetStateException != null) {
            mLog.i(
                    "setState(%s): throwing exception set by onSetStateThrows(): %s",
                    state, mOnSetStateException);
            throw mOnSetStateException;
        }
        mState = state;
        return this;
    }

    /** When called, {@link #getState()} will throw the given exception. */
    public void onGetStateThrows(@Nullable RuntimeException exception) {
        mOnGetStateException = exception;
    }

    /** When called, {@link #setState()} will throw the given exception. */
    public void onSetStateThrows(@Nullable RuntimeException exception) {
        mOnSetStateException = exception;
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder("FakeSdkSandbox[mState=").append(mState);
        if (mOnGetStateException != null) {
            string.append(", mOnGetStateException=").append(mOnGetStateException);
        }
        if (mOnSetStateException != null) {
            string.append(", mOnSetStateException=").append(mOnSetStateException);
        }
        return string.append(']').toString();
    }
}
