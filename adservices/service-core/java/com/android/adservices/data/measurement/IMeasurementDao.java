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

package com.android.adservices.data.measurement;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import java.time.Instant;
import java.util.List;

/**
 * Interface for Measurement related data access operations.
 */
public interface IMeasurementDao {
    /**
     * Set the transaction.
     */
    void setTransaction(ITransaction transaction);

    /**
     * Add an entry to the Trigger datastore.
     */
    void insertTrigger(@NonNull Uri attributionDestination, @NonNull Uri reportTo,
            @NonNull Uri registrant, @NonNull Long triggerTime, @NonNull Long triggerData,
            @Nullable Long dedupKey, @NonNull Long priority, @Nullable String aggregateTriggerData,
            @Nullable String aggregateValues) throws DatastoreException;

    /**
     * Returns list of ids for all pending {@link Trigger}.
     */
    List<String> getPendingTriggerIds() throws DatastoreException;

    /**
     * Queries and returns the {@link Trigger}.
     *
     * @param triggerId Id of the request Trigger
     * @return the requested Trigger
     */
    Trigger getTrigger(String triggerId) throws DatastoreException;

    /**
     * Gets the number of sources a registrant has registered.
     */
    long getNumTriggersPerRegistrant(Uri registrant) throws DatastoreException;

    /**
     * Updates the {@link Trigger.Status} value for the provided {@link Trigger}.
     */
    void updateTriggerStatus(Trigger trigger) throws DatastoreException;

    /**
     * Gets the number of triggers a registrant has registered.
     */
    long getNumSourcesPerRegistrant(Uri registrant) throws DatastoreException;

    /**
     * Add an entry to the Source datastore.
     */
    void insertSource(@NonNull Long sourceEventId, @NonNull Uri attributionSource,
            @NonNull Uri attributionDestination, @NonNull Uri reportTo, @NonNull Uri registrant,
            @NonNull Long sourceEventTime, @NonNull Long expiryTime, @NonNull Long priority,
            @NonNull Source.SourceType sourceType, @NonNull Long installAttributionWindow,
            @NonNull Long installCooldownWindow,
            @Source.AttributionMode int attributionMode,
            @Nullable String aggregateSource,
            @Nullable String aggregateFilterData) throws DatastoreException;

    /**
     * Queries and returns the list of matching {@link Source} for the provided {@link Trigger}.
     *
     * @return list of active matching sources; Null in case of SQL failure
     */
    List<Source> getMatchingActiveSources(Trigger trigger) throws DatastoreException;

    /**
     * Updates the {@link Source.Status} value for the provided list of {@link Source}
     *
     * @param sources list of sources.
     * @param status  value to be set
     */
    void updateSourceStatus(List<Source> sources, @Source.Status int status)
            throws DatastoreException;

    /**
     * Update the value of {@link Source.Status} for the corresponding {@link Source}
     *
     * @param source the {@link Source} object.
     */
    void updateSourceDedupKeys(Source source) throws DatastoreException;

    /**
     * Returns list of all the reports associated with the {@link Source}.
     *
     * @param source for querying reports
     * @return list of relevant eventReports
     */
    List<EventReport> getSourceEventReports(Source source) throws DatastoreException;

    /**
     * Queries and returns the {@link EventReport}.
     *
     * @param eventReportId Id of the request Event Report
     * @return the requested Event Report; Null in case of SQL failure
     */
    @Nullable
    EventReport getEventReport(String eventReportId) throws DatastoreException;

    /**
     * Change the status of an event report to DELIVERED
     *
     * @param eventReportId the id of the event report to be updated
     */
    void markEventReportDelivered(String eventReportId) throws DatastoreException;

    /**
     * Saves the {@link EventReport} to datastore.
     */
    void insertEventReport(EventReport eventReport) throws DatastoreException;

    /**
     * Deletes the {@link EventReport} from datastore.
     */
    void deleteEventReport(EventReport eventReport) throws DatastoreException;

    /**
     * Returns list of all event reports that have a scheduled reporting time in the given window.
     */
    List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException;

    /**
     * Returns list of all pending event reports for a given app right away.
     */
    List<String> getPendingEventReportIdsForGivenApp(Uri appName) throws DatastoreException;

    /**
     * Find the number of entries for a rate limit window using the {@link Source} and
     * {@link Trigger}.
     * Rate-Limit Window: (Source Site, Destination Site, Window) from triggerTime.
     *
     * @return the number of entries for the window.
     */
    long getAttributionsPerRateLimitWindow(Source source, Trigger trigger)
            throws DatastoreException;

    /**
     * Add an entry in AttributionRateLimit datastore for the provided {@link Source} and
     * {@link Trigger}
     */
    void insertAttributionRateLimit(Source source, Trigger trigger) throws DatastoreException;

    /**
     * Given one postback urls, queries and returns all the postback urls with the same adtech id.
     *
     * @param postbackUrl the postback url of the request AdtechUrl
     * @return all the postback urls with the same adtech id; Null in case of SQL failure
     */
    List<String> getAllAdtechUrls(String postbackUrl) throws DatastoreException;

    /**
     * Queries and returns the {@link AdtechUrl}.
     *
     * @param postbackUrl the postback Url of the request AdtechUrl
     * @return the requested AdtechUrl; Null in case of SQL failure
     */
    @Nullable
    AdtechUrl getAdtechEnrollmentData(String postbackUrl) throws DatastoreException;

    /**
     * Saves the {@link AdtechUrl} to datastore.
     */
    void insertAdtechUrl(AdtechUrl adtechUrl) throws DatastoreException;

    /**
     * Deletes the {@link AdtechUrl} from datastore using the given postback url.
     */
    void deleteAdtechUrl(String postbackUrl) throws DatastoreException;

    /**
     * Deletes all records in measurement tables that correspond with the provided Uri.
     *
     * @param uri the Uri to match on
     */
    void deleteAppRecords(Uri uri) throws DatastoreException;

    /**
     * Deletes all expired records in measurement tables.
     */
    void deleteExpiredRecords() throws DatastoreException;

    /**
     * Deletes all measurement data owned by a registrant and optionally providing an origin uri
     * and/or a range of dates.
     *
     * @param registrant who owns the data
     * @param origin uri for deletion. May be null
     * @param start time for deletion range. May be null. If null, end must be null as well
     * @param end time for deletion range. May be null. If null, start must be null as well
     */
    void deleteMeasurementData(
            @NonNull Uri registrant,
            @Nullable Uri origin,
            @Nullable Instant start,
            @Nullable Instant end) throws DatastoreException;

    /**
     * Mark relevant source as install attributed.
     *
     * @param uri            package identifier
     * @param eventTimestamp timestamp of installation event
     */
    void doInstallAttribution(Uri uri, long eventTimestamp) throws DatastoreException;

    /**
     * Undo any install attributed source events.
     *
     * @param uri            package identifier
     */
    void undoInstallAttribution(Uri uri) throws DatastoreException;
}
