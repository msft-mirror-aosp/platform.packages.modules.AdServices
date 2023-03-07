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
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.util.Enrollment;


import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Class containing static functions for enqueueing AsyncRegistrations */
public class EnqueueAsyncRegistration {
    /**
     * Inserts an App Source or Trigger Registration request into the Async Registration Queue
     * table.
     *
     * @param registrationRequest a {@link RegistrationRequest} to be queued.
     */
    public static boolean appSourceOrTriggerRegistrationRequest(
            RegistrationRequest registrationRequest,
            boolean adIdPermission,
            Uri registrant,
            long requestTime,
            @Nullable Source.SourceType sourceType,
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
                        LogUtil.d("no enrollment data");
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
                            registrant,
                            registrationRequest.getRegistrationType()
                                            == RegistrationRequest.REGISTER_SOURCE
                                    ? AsyncRegistration.RegistrationType.APP_SOURCE
                                    : AsyncRegistration.RegistrationType.APP_TRIGGER,
                            sourceType,
                            requestTime,
                            /* mRetryCount */ 0,
                            System.currentTimeMillis(),
                            AsyncRegistration.RedirectType.ANY,
                            false,
                            adIdPermission,
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
            boolean adIdPermission,
            Uri registrant,
            long requestTime,
            @Nullable Source.SourceType sourceType,
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
                            LogUtil.d("no enrollment data");
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
                                sourceType,
                                requestTime,
                                /* mRetryCount */ 0,
                                System.currentTimeMillis(),
                                AsyncRegistration.RedirectType.NONE,
                                webSourceParams.isDebugKeyAllowed(),
                                adIdPermission,
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
            boolean adIdPermission,
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
                            LogUtil.d("no enrollment data");
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
                                AsyncRegistration.RedirectType.NONE,
                                webTriggerParams.isDebugKeyAllowed(),
                                adIdPermission,
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
            @AsyncRegistration.RedirectType int redirectType,
            boolean debugKeyAllowed,
            boolean adIdPermission,
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
                        .setRedirectType(redirectType)
                        .setDebugKeyAllowed(debugKeyAllowed)
                        .setAdIdPermission(adIdPermission)
                        .build();

        dao.insertAsyncRegistration(asyncRegistration);
    }
}
