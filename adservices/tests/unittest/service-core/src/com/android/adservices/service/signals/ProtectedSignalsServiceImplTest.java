/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals;

import android.adservices.signals.FetchSignalUpdatesCallback;
import android.adservices.signals.FetchSignalUpdatesInput;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

// TODO(b/295270013) Add tests once implemented.
public class ProtectedSignalsServiceImplTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Test
    public void testFetchSignalUpdates() throws Exception {
        ProtectedSignalsServiceImpl.create(CONTEXT)
                .fetchSignalUpdates(
                        new FetchSignalUpdatesInput.Builder(Uri.parse("example.com"), "package")
                                .build(),
                        new FetchSignalUpdatesCallback.Default());
    }
}
