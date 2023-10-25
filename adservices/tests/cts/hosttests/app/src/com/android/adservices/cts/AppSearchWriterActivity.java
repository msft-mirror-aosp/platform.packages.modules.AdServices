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
package com.android.adservices.cts;

import android.adservices.measurement.MeasurementManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.platformstorage.PlatformStorage;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.cts.dao.AppSearchConsentDao;
import com.android.adservices.cts.dao.AppSearchNotificationDao;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppSearchWriterActivity extends AppCompatActivity {
    private static final String TAG = "AppSearchWriterActivity";
    private static final String NOTIFICATION_DATABASE_NAME = "adservices_notification";
    private static final String CONSENT_DATABASE_NAME = "adservices_consent";

    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    private AppSearchDaoWriter mSearchDaoWriter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        String userId = intent.getStringExtra("user-id");
        if (userId == null || userId.isBlank()) {
            Log.e(TAG, "Missing intent extra for user-id");
            return;
        }

        String adServicesPackageName = AdservicesTestHelper.getAdServicesPackageName(this);
        mSearchDaoWriter = new AppSearchDaoWriter(adServicesPackageName);

        // Read various values from intent if specified
        boolean notification = intent.getBooleanExtra("notification-displayed", true);
        boolean msmtConsent = intent.getBooleanExtra("measurement-consent", true);
        boolean topicsConsent = intent.getBooleanExtra("topics-consent", true);
        boolean fledgeConsent = intent.getBooleanExtra("fledge-consent", false);
        boolean triggerApis = intent.getBooleanExtra("call-api", true);

        // Write data to AppSearch
        recordGaUxNotificationDisplayed(userId, notification);
        setConsent(userId, "CONSENT-TOPICS", topicsConsent);
        setConsent(userId, "CONSENT-MEASUREMENT", msmtConsent);
        setConsent(userId, "CONSENT-FLEDGE", fledgeConsent);

        // Trigger consent migration by calling the apis if specified.
        if (triggerApis) {
            callMeasurementApi();
        }
    }

    private void recordGaUxNotificationDisplayed(String userId, boolean wasNotificationDisplayed) {
        ListenableFuture<AppSearchSession> session =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(this, NOTIFICATION_DATABASE_NAME)
                                .build());

        AppSearchNotificationDao dao =
                new AppSearchNotificationDao(
                        userId,
                        userId,
                        AppSearchNotificationDao.NAMESPACE,
                        /* wasNotificationDisplayed= */ wasNotificationDisplayed,
                        /* wasGaUxNotificationDisplayed= */ wasNotificationDisplayed);
        mSearchDaoWriter.writeToAppSearch(dao, session, "notification");
    }

    private void setConsent(@NonNull String userId, @NonNull String apiType, boolean consented) {
        Objects.requireNonNull(apiType);

        ListenableFuture<AppSearchSession> session =
                PlatformStorage.createSearchSessionAsync(
                        new PlatformStorage.SearchContext.Builder(this, CONSENT_DATABASE_NAME)
                                .build());

        AppSearchConsentDao dao =
                new AppSearchConsentDao(
                        AppSearchConsentDao.getRowId(userId, apiType),
                        userId,
                        AppSearchConsentDao.NAMESPACE,
                        apiType,
                        Boolean.toString(consented));
        mSearchDaoWriter.writeToAppSearch(dao, session, "consent");
    }

    private void callMeasurementApi() {
        Log.d(TAG, "Calling Measurement api");
        MeasurementManager mgr = MeasurementManager.get(this);
        mgr.getMeasurementApiStatus(EXECUTOR, getOutcomeReceiver("GetMeasurementStatus"));
    }

    private <T> OutcomeReceiver<T, Exception> getOutcomeReceiver(String prefix) {
        return new OutcomeReceiver<>() {
            @Override
            public void onResult(T result) {
                Log.d(TAG, prefix + " API call succeeded");
            }

            @Override
            public void onError(@NonNull Exception e) {
                Log.e(TAG, prefix + " API call failed", e);
            }
        };
    }
}
