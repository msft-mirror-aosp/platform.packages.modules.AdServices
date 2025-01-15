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

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/** Action used to set the value of a {@link NameValuePair} . */
public final class NameValuePairAction extends AbstractAction {

    private final NameValuePairSetter mSetter;
    private final NameValuePair mNvp;

    @Nullable private NameValuePair mPreviousNvp;
    // We need mSetb because when previous value was null, we need to remove it on revert
    private boolean mSet;

    protected NameValuePairAction(Logger logger, NameValuePairSetter setter, NameValuePair nvp) {
        super(logger);

        mSetter = Objects.requireNonNull(setter, "setter cannot be null");
        mNvp = Objects.requireNonNull(nvp, "nvp cannot be null");
    }

    /** Gets the {@code nvp} that will be set by the action. */
    public NameValuePair getNvp() {
        return mNvp;
    }

    @VisibleForTesting
    @Nullable
    NameValuePair getPreviousNvp() {
        return mPreviousNvp;
    }

    @Override
    protected boolean onExecuteLocked() throws Exception {
        try {
            mPreviousNvp = mSetter.set(mNvp);
        } catch (Exception e) {
            mLog.e(e, "%s: failed to set %s; it won't be restored at the end", this, mNvp);
            return false;
        }
        if (mNvp.equals(mPreviousNvp)) {
            mLog.d("%s: not setting when it's already %s", this, mNvp);
            mPreviousNvp = null;
            return false;
        }
        mSet = true;
        return true;
    }

    @Override
    protected void onRevertLocked() throws Exception {
        if (!mSet) {
            throw new IllegalStateException("should not have been called when it didn't change");
        }
        if (mPreviousNvp == null) {
            mSetter.remove(mNvp.name);
            return;
        }
        mSetter.set(mPreviousNvp);
    }

    @Override
    protected void onResetLocked() {
        mPreviousNvp = null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNvp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        NameValuePairAction other = (NameValuePairAction) obj;
        return Objects.equals(mNvp, other.mNvp);
    }

    @Override
    public String toString() {
        return "NameValuePairAction[nvp="
                + mNvp
                + ", previousNvp="
                + mPreviousNvp
                + ", set="
                + mSet
                + ']';
    }
}
