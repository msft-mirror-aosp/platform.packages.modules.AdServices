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

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Class used to send debug reports to Ad-Tech {@link DebugReport} */
public class DebugReportApi {

    private final Context mContext;
    private final DatastoreManager mDatastoreManager;

    DebugReportApi(Context context) {
        mContext = context;
        mDatastoreManager = DatastoreManagerFactory.getDatastoreManager(context);
    }

    @VisibleForTesting
    DebugReportApi(Context context, DatastoreManager datastoreManager) {
        mContext = context;
        mDatastoreManager = datastoreManager;
    }

    /**
     * Schedules the Debug Report to be sent
     *
     * @param type The type of the debug report
     * @param body The body of the debug report
     * @param enrollmentId Ad Tech enrollment ID
     * @param isAdTechOptIn Ad Tech opt-in to receiving debug reports
     */
    public void scheduleReport(
            @NonNull String type,
            @NonNull Map<String, String> body,
            @NonNull String enrollmentId,
            boolean isAdTechOptIn) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(body);
        Objects.requireNonNull(enrollmentId);
        if (!isAdTechOptIn) {
            LogUtil.d("Ad-Tech not opted in to debug reports");
            return;
        }
        if (type.isEmpty() || body.isEmpty()) {
            LogUtil.d("Empty debug report found");
            return;
        }
        if (enrollmentId.isEmpty()) {
            LogUtil.d("Empty enrollment found");
            return;
        }
        DebugReport debugReport =
                new DebugReport.Builder()
                        .setId(UUID.randomUUID().toString())
                        .setType(type)
                        .setBody(new JSONObject(body).toString())
                        .setEnrollmentId(enrollmentId)
                        .build();
        mDatastoreManager.runInTransaction(
                measurementDao -> {
                    measurementDao.insertDebugReport(debugReport);
                });
        DebugReportingJobService.scheduleIfNeeded(
                mContext, /*forceSchedule=*/ true, /*isDebugReportApi=*/ true);
    }
}
