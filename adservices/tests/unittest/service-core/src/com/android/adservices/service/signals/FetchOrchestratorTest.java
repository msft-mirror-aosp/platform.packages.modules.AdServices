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

import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME_1;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FetchOrchestratorTest {

    private static final Uri URI = Uri.parse("https://example.com");
    private static final String JSON = "{\"a\":\"b\"}";
    @Mock private UpdatesDownloader mUpdatesDownloader;
    @Mock private UpdatesProcessor mUpdatesProcessor;

    private FetchOrchestrator mFetchOrchestrator;

    @Before
    public void setup() {
        mFetchOrchestrator =
                new FetchOrchestrator(
                        AdServicesExecutors.getBackgroundExecutor(),
                        mUpdatesDownloader,
                        mUpdatesProcessor);
    }

    @Test
    public void testOrchestrateFetch() throws Exception {
        SettableFuture future = SettableFuture.create();
        future.set(new JSONObject(JSON));
        FluentFuture<JSONObject> returnValue = FluentFuture.from(future);
        when(mUpdatesDownloader.getUpdateJson(URI, TEST_PACKAGE_NAME_1)).thenReturn(returnValue);

        mFetchOrchestrator.orchestrateFetch(URI, TEST_PACKAGE_NAME_1);

        verify(mUpdatesDownloader).getUpdateJson(eq(URI), eq(TEST_PACKAGE_NAME_1));
        verify(mUpdatesProcessor).processUpdates(any(JSONObject.class));
    }
}
