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

package com.android.adservices.service.measurement.reporting;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * A wrapper class to expose a constructor for AggregateReportingJobHandler in testing.
 */
public class AggregateReportingJobHandlerWrapper {
    public static Object[] spyPerformScheduledPendingReportsInWindow(
            DatastoreManager datastoreManager, long windowStartTime, long windowEndTime)
            throws IOException, JSONException {
        // Set up aggregate reporting job handler spy
        AggregateReportingJobHandler aggregateReportingJobHandler =
                Mockito.spy(new AggregateReportingJobHandler(datastoreManager));
        Mockito.doReturn(200).when(aggregateReportingJobHandler)
                .makeHttpPostRequest(any(), any());

        // Perform aggregate reports and capture arguments
        aggregateReportingJobHandler.performScheduledPendingReportsInWindow(
                windowStartTime, windowEndTime);

        ArgumentCaptor<Uri> aggregateDestination = ArgumentCaptor.forClass(Uri.class);
        ArgumentCaptor<JSONObject> aggregatePayload = ArgumentCaptor.forClass(JSONObject.class);
        verify(aggregateReportingJobHandler, atLeast(0))
                .makeHttpPostRequest(aggregateDestination.capture(), aggregatePayload.capture());

        // Collect actual reports
        return new Object[]{
                aggregateDestination.getAllValues(),
                aggregatePayload.getAllValues()
        };
    }
}
