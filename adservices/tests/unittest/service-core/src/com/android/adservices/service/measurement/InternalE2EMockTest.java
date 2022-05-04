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

package com.android.adservices.service.measurement;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.test.mock.MockContentResolver;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.service.measurement.InternalE2ETest.TestFormatJsonMapping;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.ReportingJob;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.TriggerFetcher;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 */
@RunWith(Parameterized.class)
public class InternalE2EMockTest extends InternalE2ETest {
    private SourceFetcher mSourceFetcher;
    private TriggerFetcher mTriggerFetcher;

    public InternalE2EMockTest(Collection<Action> actions, ReportObjects expectedOutput,
            String name) throws DatastoreException {
        super(actions, expectedOutput, name);
        this.mSourceFetcher = Mockito.spy(new SourceFetcher());
        this.mTriggerFetcher = Mockito.spy(new TriggerFetcher());
        this.mMeasurementImpl = new MeasurementImpl(
            new MockContentResolver(), sDatastoreManager, mSourceFetcher, mTriggerFetcher);
    }

    @Override
    void prepareRegistrationServer(RegisterSource sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Mockito.doAnswer(new Answer<Map<String, List<String>>>() {
                public Map<String, List<String>> answer(InvocationOnMock invocation) {
                    List<Map<String, List<String>>> responseList =
                            sourceRegistration.mUriToResponseHeadersMap.get(uri);
                    Map<String, List<String>> headersMap = responseList.get(0);
                    responseList.remove(0);
                    return headersMap;
                }
            }).when(urlConnection).getHeaderFields();
            when(mSourceFetcher.openUrl(new URL(uri))).thenReturn(urlConnection);
        }
    }

    @Override
    void prepareRegistrationServer(RegisterTrigger triggerRegistration)
            throws IOException {
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Mockito.doAnswer(new Answer<Map<String, List<String>>>() {
                public Map<String, List<String>> answer(InvocationOnMock invocation) {
                    List<Map<String, List<String>>> responseList =
                            triggerRegistration.mUriToResponseHeadersMap.get(uri);
                    Map<String, List<String>> headersMap = responseList.get(0);
                    responseList.remove(0);
                    return headersMap;
                }
            }).when(urlConnection).getHeaderFields();
            when(mTriggerFetcher.openUrl(new URL(uri))).thenReturn(urlConnection);
        }
    }

    @Override
    void processAction(ReportingJob reportingJob) throws IOException, JSONException {
        // Set up event reporting job handler spy
        EventReportingJobHandler eventReportingJobHandler =
                Mockito.spy(new EventReportingJobHandler(sDatastoreManager));
        Mockito.doReturn(200).when(eventReportingJobHandler)
                .makeHttpPostRequest(any(), any());

        // Perform event reports and capture arguments
        eventReportingJobHandler.performScheduledPendingReportsInWindow(
                reportingJob.mTimestamp
                - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                    reportingJob.mTimestamp);
        ArgumentCaptor<Uri> destination = ArgumentCaptor.forClass(Uri.class);
        ArgumentCaptor<EventReport> eventReport = ArgumentCaptor.forClass(EventReport.class);
        verify(eventReportingJobHandler, atLeast(0))
                .createReportJsonPayload(eventReport.capture());
        ArgumentCaptor<JSONObject> payload = ArgumentCaptor.forClass(JSONObject.class);
        verify(eventReportingJobHandler, atLeast(0))
                .makeHttpPostRequest(destination.capture(), payload.capture());

        // Collect actual reports
        List<JSONObject> eventReportObjects = getEventReportObjects(
                eventReport.getAllValues(), destination.getAllValues(), payload.getAllValues());
        mActualOutput.mEventReportObjects.addAll(eventReportObjects);
    }

    private List<JSONObject> getEventReportObjects(
            List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            Map<String, Object> map = new HashMap<>();
            map.put(TestFormatJsonMapping.REPORT_TIME_KEY, eventReports.get(i).getReportTime());
            map.put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString());
            map.put(TestFormatJsonMapping.PAYLOAD_KEY, payloads.get(i));
            result.add(new JSONObject(map));
        }
        return result;
    }
}
