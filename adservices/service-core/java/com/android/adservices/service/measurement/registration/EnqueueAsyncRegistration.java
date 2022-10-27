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

package com.android.adservices.service.measurement.registration;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.annotation.NonNull;
import android.net.Uri;
import android.view.InputEvent;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.util.Enrollment;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Class containing static functions for enqueueing AsyncRegistrations */
public class EnqueueAsyncRegistration {
    private static final String ANDROID_APP_SCHEME = "android-app";

    /**
     * Inserts an App Source or Trigger Registration request into the Async Registration Queue
     * table.
     *
     * @param registrationRequest a {@link RegistrationRequest} to be queued.
     */
    public static boolean appSourceOrTriggerRegistrationRequest(
            RegistrationRequest registrationRequest,
            Uri registrant,
            long requestTime,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull DatastoreManager datastoreManager) {
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(datastoreManager);
        return datastoreManager.runInTransaction(
                (dao) -> {
                    Optional<String> enrollmentData =
                            Enrollment.maybeGetEnrollmentId(
                                    registrationRequest.getRegistrationUri(), enrollmentDao);
                    if (enrollmentData == null || enrollmentData.isEmpty()) {
                        return;
                    }
                    String enrollmentId = enrollmentData.get();
                    insertAsyncRegistration(
                            UUID.randomUUID().toString(),
                            enrollmentId,
                            registrationRequest.getRegistrationUri(),
                            /* mWebDestination */ null,
                            /* mOsDestination */ null,
                            registrant,
                            /* verifiedDestination */ null,
                            getPublisher(registrationRequest),
                            registrationRequest.getRegistrationType()
                                            == RegistrationRequest.REGISTER_SOURCE
                                    ? AsyncRegistration.RegistrationType.APP_SOURCE
                                    : AsyncRegistration.RegistrationType.APP_TRIGGER,
                            registrationRequest.getRegistrationType()
                                            == RegistrationRequest.REGISTER_TRIGGER
                                    ? null
                                    : getSourceType(registrationRequest.getInputEvent()),
                            requestTime,
                            /* mRetryCount */ 0,
                            System.currentTimeMillis(),
                            /* mRedirect */ true,
                            registrationRequest.isAdIdPermissionGranted(),
                            dao);
                });
    }

    /**
     * Inserts a Web Source Registration request into the Async Registration Queue table.
     *
     * @param webSourceRegistrationRequest a {@link WebSourceRegistrationRequest} to be queued.
     */
    public static boolean webSourceRegistrationRequest(
            WebSourceRegistrationRequest webSourceRegistrationRequest,
            Uri registrant,
            long requestTime,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull DatastoreManager datastoreManager) {
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(datastoreManager);
        return datastoreManager.runInTransaction(
                (dao) -> {
                    for (WebSourceParams webSourceParams :
                            webSourceRegistrationRequest.getSourceParams()) {
                        Optional<String> enrollmentData =
                                Enrollment.maybeGetEnrollmentId(
                                        webSourceParams.getRegistrationUri(), enrollmentDao);
                        if (enrollmentData == null || enrollmentData.isEmpty()) {
                            return;
                        }
                        String enrollmentId = enrollmentData.get();

                        insertAsyncRegistration(
                                UUID.randomUUID().toString(),
                                enrollmentId,
                                webSourceParams.getRegistrationUri(),
                                webSourceRegistrationRequest.getWebDestination(),
                                webSourceRegistrationRequest.getAppDestination(),
                                registrant,
                                webSourceRegistrationRequest.getVerifiedDestination(),
                                webSourceRegistrationRequest.getTopOriginUri(),
                                AsyncRegistration.RegistrationType.WEB_SOURCE,
                                getSourceType(webSourceRegistrationRequest.getInputEvent()),
                                requestTime,
                                /* mRetryCount */ 0,
                                System.currentTimeMillis(),
                                /* mRedirect */ false,
                                webSourceParams.isDebugKeyAllowed(),
                                dao);
                    }
                });
    }

    /**
     * Inserts a Web Trigger Registration request into the Async Registration Queue table.
     *
     * @param webTriggerRegistrationRequest a {@link WebTriggerRegistrationRequest} to be queued.
     */
    public static boolean webTriggerRegistrationRequest(
            WebTriggerRegistrationRequest webTriggerRegistrationRequest,
            Uri registrant,
            long requestTime,
            @NonNull EnrollmentDao enrollmentDao,
            @NonNull DatastoreManager datastoreManager) {
        Objects.requireNonNull(enrollmentDao);
        Objects.requireNonNull(datastoreManager);
        return datastoreManager.runInTransaction(
                (dao) -> {
                    for (WebTriggerParams webTriggerParams :
                            webTriggerRegistrationRequest.getTriggerParams()) {
                        Optional<String> enrollmentData =
                                Enrollment.maybeGetEnrollmentId(
                                        webTriggerParams.getRegistrationUri(), enrollmentDao);
                        if (enrollmentData == null || enrollmentData.isEmpty()) {
                            return;
                        }
                        String enrollmentId = enrollmentData.get();

                        insertAsyncRegistration(
                                UUID.randomUUID().toString(),
                                enrollmentId,
                                webTriggerParams.getRegistrationUri(),
                                /* mWebDestination */ null,
                                /* mOsDestination */ null,
                                registrant,
                                /* mVerifiedDestination */ null,
                                webTriggerRegistrationRequest.getDestination(),
                                AsyncRegistration.RegistrationType.WEB_TRIGGER,
                                /* mSourceType */ null,
                                requestTime,
                                /* mRetryCount */ 0,
                                System.currentTimeMillis(),
                                /* mRedirect */ false,
                                webTriggerParams.isDebugKeyAllowed(),
                                dao);
                    }
                });
    }

    private static void insertAsyncRegistration(
            String iD,
            String enrollmentId,
            Uri registrationUri,
            Uri webDestination,
            Uri osDestination,
            Uri registrant,
            Uri verifiedDestination,
            Uri topOrigin,
            AsyncRegistration.RegistrationType registrationType,
            Source.SourceType sourceType,
            long mRequestTime,
            long mRetryCount,
            long mLastProcessingTime,
            boolean redirect,
            boolean debugKeyAllowed,
            @NonNull IMeasurementDao dao)
            throws DatastoreException {
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setId(iD)
                        .setEnrollmentId(enrollmentId)
                        .setRegistrationUri(registrationUri)
                        .setWebDestination(webDestination)
                        .setOsDestination(osDestination)
                        .setRegistrant(registrant)
                        .setVerifiedDestination(verifiedDestination)
                        .setTopOrigin(topOrigin)
                        .setType(registrationType.ordinal())
                        .setSourceType(sourceType)
                        .setRequestTime(mRequestTime)
                        .setRetryCount(mRetryCount)
                        .setLastProcessingTime(mLastProcessingTime)
                        .setRedirect(redirect)
                        .setDebugKeyAllowed(debugKeyAllowed)
                        .build();

        dao.insertAsyncRegistration(asyncRegistration);
    }

    @VisibleForTesting
    static Source.SourceType getSourceType(InputEvent inputEvent) {
        return inputEvent == null ? Source.SourceType.EVENT : Source.SourceType.NAVIGATION;
    }

    private static Uri getPublisher(RegistrationRequest request) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + request.getPackageName());
    }
}
