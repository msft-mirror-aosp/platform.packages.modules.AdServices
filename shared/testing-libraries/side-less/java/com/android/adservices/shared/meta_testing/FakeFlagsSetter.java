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

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FakeFlagsSetter implements Consumer<NameValuePair> {

    private final Logger mLog = new Logger(DynamicLogger.getInstance(), getClass());

    private final List<NameValuePair> mCalls = new ArrayList<>();

    @Override
    public void accept(NameValuePair nvp) {
        mLog.d("setting %s", nvp);
        mCalls.add(nvp);
    }

    /** Gets all calls receive so far. */
    public ImmutableList<NameValuePair> getCalls() {
        return ImmutableList.copyOf(mCalls);
    }

    /** Gets all calls receive so far, and resets them internally. */
    public ImmutableList<NameValuePair> getAndResetCalls() {
        var calls = getCalls();
        mCalls.clear();
        return calls;
    }
}
