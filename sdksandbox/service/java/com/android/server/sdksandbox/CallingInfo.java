/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.sdksandbox;

import java.util.Objects;

/**
 * Representation of a caller for an SDK sandbox.
 * @hide
 */
public final class CallingInfo {

    private final int mUid;
    private final String mPackageName;

    public CallingInfo(int uid, String packageName) {
        mUid = uid;
        mPackageName = Objects.requireNonNull(packageName);
    }

    public int getUid() {
        return mUid;
    }

    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public String toString() {
        return "CallingInfo{" + "mUid=" + mUid + ", mPackageName='" + mPackageName + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallingInfo)) return false;
        CallingInfo that = (CallingInfo) o;
        return mUid == that.mUid && mPackageName.equals(that.mPackageName);
    }

    @Override
    public int hashCode() {
        return mUid ^ mPackageName.hashCode();
    }
}
