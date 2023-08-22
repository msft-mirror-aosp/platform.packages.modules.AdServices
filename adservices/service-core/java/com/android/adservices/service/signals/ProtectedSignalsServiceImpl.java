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
import android.adservices.signals.IProtectedSignalsService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

/** Implementation of the Protected Signals service. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ProtectedSignalsServiceImpl extends IProtectedSignalsService.Stub {

    @Override
    public void fetchSignalUpdates(
            @NonNull FetchSignalUpdatesInput fetchSignalUpdatesInput,
            @NonNull FetchSignalUpdatesCallback fetchSignalUpdatesCallback)
            throws RemoteException {
        // TODO(b/295270013) Implement this
    }

    /** Creates a new instance of {@link ProtectedSignalsServiceImpl}. */
    public static ProtectedSignalsServiceImpl create(@NonNull Context context) {
        return new ProtectedSignalsServiceImpl();
    }
}
