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

package com.android.adservices.service.adselection.debug;

import android.net.Uri;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

/** Provides methods to send debug report URIs to buyers and sellers */
public interface DebugReportSenderStrategy {

    /** Adds the URI to the queue of debug reports to be sent. */
    void enqueue(Uri uri);

    /** Adds all the URIs provided to the queue of debug reports to be sent. */
    void batchEnqueue(List<Uri> uris);

    /** flushes all the URIs currently enqueued to be sent as debug reports. */
    ListenableFuture<Void> flush();
}
