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

import android.annotation.NonNull;
import android.net.Uri;

import com.google.common.util.concurrent.FluentFuture;

import org.json.JSONObject;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Orchestrates the fetchSignalUpdates API. */
public class FetchOrchestrator {

    @NonNull private final Executor mBackgroundExecutor;
    @NonNull private final UpdatesDownloader mUpdatesDownloader;
    @NonNull private final UpdatesProcessor mUpdatesProcessor;

    public FetchOrchestrator(
            Executor backgroundExecutor,
            UpdatesDownloader updatesDownloader,
            UpdatesProcessor updatesProcessor) {
        Objects.requireNonNull(backgroundExecutor);
        mBackgroundExecutor = backgroundExecutor;
        Objects.requireNonNull(updatesProcessor);
        mUpdatesProcessor = updatesProcessor;
        Objects.requireNonNull(updatesDownloader);
        mUpdatesDownloader = updatesDownloader;
    }

    /**
     * Orchestrate the fetchSignalsUpdate API.
     *
     * @param validatedUri Validated Uri to fetch JSON from.
     * @param packageName The package name of the calling app.
     */
    public void orchestrateFetch(Uri validatedUri, String packageName) {
        FluentFuture<JSONObject> jsonFuture =
                mUpdatesDownloader.getUpdateJson(validatedUri, packageName);
        // We don't care about the return value here
        var unused =
                jsonFuture.transform(
                        x -> {
                            mUpdatesProcessor.processUpdates(x);
                            return null;
                        },
                        mBackgroundExecutor);
    }
}
