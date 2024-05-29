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
package com.android.adservices.shared.testing.concurrency;

import org.junit.Test;

public final class SyncOnSharedPreferenceChangeListenerTest
        extends IResultSyncCallbackTestCase<String, SyncOnSharedPreferenceChangeListener> {

    @Override
    protected String newResult() {
        return getNextUniqueId() + "- I listen, therefore I prefer!";
    }

    @Override
    protected SyncOnSharedPreferenceChangeListener newCallback(SyncCallbackSettings settings) {
        return new SyncOnSharedPreferenceChangeListener();
    }

    @Test
    public void testOnSharedPreferenceChanged() throws Exception {
        String key = "on song of X";

        mCallback.onSharedPreferenceChanged(/* sharedPreferences= */ null, key);
        String result = mCallback.assertResultReceived();

        expect.withMessage("assertResultReceived()").that(result).isSameInstanceAs(key);
    }
}
