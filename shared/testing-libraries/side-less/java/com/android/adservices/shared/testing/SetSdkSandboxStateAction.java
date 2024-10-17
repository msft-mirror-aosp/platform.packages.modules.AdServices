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

import static com.android.adservices.shared.testing.SdkSandbox.State.UNSUPPORTED;

import com.android.adservices.shared.testing.SdkSandbox.State;

import java.util.Objects;

/** Action used to set {@code SdkSandbox}'s state. */
public final class SetSdkSandboxStateAction extends AbstractAction {

    private final SdkSandbox mSdkSandbox;
    private final State mState;

    @Nullable private State mPreviousState;

    public SetSdkSandboxStateAction(Logger logger, SdkSandbox sandbox, State state) {
        super(logger);
        mSdkSandbox = Objects.requireNonNull(sandbox, "sandbox cannot be null");
        mState = Objects.requireNonNull(state, "state cannot be null");
        if (!state.isValid()) {
            throw new IllegalArgumentException("Invalid state: " + state);
        }
    }

    @Override
    protected boolean onExecute() throws Exception {
        try {
            mPreviousState = mSdkSandbox.getState();
        } catch (Exception e) {
            mLog.e(e, "%s: failed to get state; it won't be restored at the end", this);
        }
        if (mState.equals(mPreviousState)) {
            mLog.d("onExecute(): state didn't change: %s", mState);
            mPreviousState = null;
            return false;
        }
        if (UNSUPPORTED.equals(mPreviousState)) {
            mLog.d("onExecute(): previous state is UNSUPPORTED, ignored");
            return false;
        }
        mSdkSandbox.setState(mState);
        return mPreviousState != null && mPreviousState.isValid();
    }

    @Override
    protected void onRevert() throws Exception {
        if (mPreviousState == null || !mPreviousState.isValid()) {
            throw new IllegalStateException("should not have been called when it didn't change");
        }
        mSdkSandbox.setState(mPreviousState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mState);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SetSdkSandboxStateAction other = (SetSdkSandboxStateAction) obj;
        return mState == other.mState;
    }

    @Override
    public String toString() {
        return "SetSdkSandboxStateAction[state="
                + mState
                + ", previousState="
                + mPreviousState
                + ']';
    }
}
