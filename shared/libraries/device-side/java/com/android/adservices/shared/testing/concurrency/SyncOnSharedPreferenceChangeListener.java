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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

/** OnSharedPreferenceChangeListener implementation that blocks until the first key is received. */
public final class SyncOnSharedPreferenceChangeListener extends ResultSyncCallback<String>
        implements OnSharedPreferenceChangeListener {

    public SyncOnSharedPreferenceChangeListener() {
        super();
    }

    public SyncOnSharedPreferenceChangeListener(SyncCallbackSettings settings) {
        super(settings);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        injectResult(key);
    }
}