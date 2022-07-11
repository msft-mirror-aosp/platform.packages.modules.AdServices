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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.ReportingJob;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobHandlerWrapper;
import com.android.adservices.service.measurement.reporting.EventReportingJobHandlerWrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
 *
 * Consider @RunWith(Parameterized.class)
 */
public abstract class E2EMockTest extends E2ETest {
    SourceFetcher mSourceFetcher;
    TriggerFetcher mTriggerFetcher;

    E2EMockTest(Collection<Action> actions, ReportObjects expectedOutput,
            String name) throws DatastoreException {
        super(actions, expectedOutput, name);
        mSourceFetcher = Mockito.spy(new SourceFetcher());
        mTriggerFetcher = Mockito.spy(new TriggerFetcher());
    }

    @Override
    void prepareRegistrationServer(RegisterSource sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            Mockito.doAnswer(new Answer<Map<String, List<String>>>() {
                public Map<String, List<String>> answer(InvocationOnMock invocation) {
                    return sourceRegistration.getNextResponse(uri);
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
                    return triggerRegistration.getNextResponse(uri);
                }
            }).when(urlConnection).getHeaderFields();
            when(mTriggerFetcher.openUrl(new URL(uri))).thenReturn(urlConnection);
        }
    }

    @Override
    void processAction(ReportingJob reportingJob) throws IOException, JSONException {
        Object[] eventCaptures = EventReportingJobHandlerWrapper
                .spyPerformScheduledPendingReportsInWindow(
                        sDatastoreManager,
                        reportingJob.mTimestamp
                                - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                        reportingJob.mTimestamp);

        Object[] aggregateCaptures = AggregateReportingJobHandlerWrapper
                .spyPerformScheduledPendingReportsInWindow(
                        sDatastoreManager,
                        reportingJob.mTimestamp
                                - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                        reportingJob.mTimestamp);

        processEventReports(
                (List<EventReport>) eventCaptures[0],
                (List<Uri>) eventCaptures[1],
                (List<JSONObject>) eventCaptures[2]);

        processAggregateReports(
                (List<Uri>) aggregateCaptures[0],
                (List<JSONObject>) aggregateCaptures[1]);
    }

    // Class extensions may need different processing to prepare for result evaluation.
    void processEventReports(List<EventReport> eventReports, List<Uri> destinations,
            List<JSONObject> payloads) throws JSONException {
        List<JSONObject> eventReportObjects =
                getEventReportObjects(eventReports, destinations, payloads);
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

    // Class extensions may need different processing to prepare for result evaluation.
    void processAggregateReports(List<Uri> destinations, List<JSONObject> payloads)
            throws JSONException {
        List<JSONObject> aggregateReportObjects = getAggregateReportObjects(destinations, payloads);
        mActualOutput.mAggregateReportObjects.addAll(aggregateReportObjects);
    }

    private List<JSONObject> getAggregateReportObjects(List<Uri> destinations,
            List<JSONObject> payloads) throws JSONException {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            JSONObject sharedInfo = new JSONObject(payloads.get(i).getString("shared_info"));
            result.add(new JSONObject()
                    .put(TestFormatJsonMapping.REPORT_TIME_KEY,
                            sharedInfo.getLong("scheduled_report_time") * 1000)
                    .put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString())
                    .put(TestFormatJsonMapping.PAYLOAD_KEY,
                            getAggregatablePayloadForTest(sharedInfo, payloads.get(i))));
        }
        return result;
    }

    private static JSONObject getAggregatablePayloadForTest(
            JSONObject sharedInfo, JSONObject data) throws JSONException {
        String payloadJson = data.getJSONArray("aggregation_service_payloads")
                .getJSONObject(0)
                .getString("debug_cleartext_payload");
        JSONArray histograms = new JSONObject(payloadJson).getJSONArray("data");
        return new JSONObject()
                .put(AggregateReportPayloadKeys.ATTRIBUTION_DESTINATION,
                        sharedInfo.getString("attribution_destination"))
                .put(AggregateReportPayloadKeys.HISTOGRAMS, getAggregateHistograms(histograms));
    }

    private static JSONArray getAggregateHistograms(JSONArray histograms) throws JSONException {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < histograms.length(); i++) {
            JSONObject obj = histograms.getJSONObject(i);
            result.add(new JSONObject()
                    .put(AggregateHistogramKeys.BUCKET, obj.getString("bucket"))
                    .put(AggregateHistogramKeys.VALUE, obj.getInt("value")));
        }
        return new JSONArray(result);
    }
}
