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

import android.adservices.measurement.DeletionRequest;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.Attribution;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;

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

    /** Add an entry to the Trigger datastore. */
    void insertTrigger(Trigger trigger) throws DatastoreException;

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
    long getNumSourcesPerRegistrant(Uri registrant) throws DatastoreException;

    /**
     * Gets the number of triggers a registrant has registered.
     */
    long getNumTriggersPerRegistrant(Uri registrant) throws DatastoreException;

    /**
     * Gets the count of distinct IDs of enrollments in the Attribution table in a time window
     * with matching publisher and destination, excluding a given enrollment ID.
     */
    Integer countDistinctEnrollmentsPerPublisherXDestinationInAttribution(Uri sourceSite,
            Uri destination, String excludedEnrollmentId, long windowStartTime, long windowEndTime)
            throws DatastoreException;

    /**
     * Gets the count of distinct Uri's of destinations in the Source table in a time window with
     * matching publisher, enrollment, and ACTIVE status, excluding a given destination.
     */
    Integer countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(Uri publisher,
            @EventSurfaceType int publisherType, String enrollmentId, Uri excludedDestination,
            @EventSurfaceType int destinationType, long windowStartTime, long windowEndTime)
            throws DatastoreException;

    /**
     * Gets the count of distinct IDs of enrollments in the Source table in a time window with
     * matching publisher and destination, excluding a given enrollment ID.
     */
    Integer countDistinctEnrollmentsPerPublisherXDestinationInSource(Uri publisher,
            @EventSurfaceType int publisherType, Uri destination, String enrollmentId,
            long windowStartTime, long windowEndTime) throws DatastoreException;

     /**
     * Updates the {@link Trigger.Status} value for the provided {@link Trigger}.
     */
    void updateTriggerStatus(Trigger trigger) throws DatastoreException;

    /**
     * Add an entry to the Source datastore.
     */
    void insertSource(Source source) throws DatastoreException;

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
     * Updates the value of aggregate contributions for the corresponding {@link Source}
     *
     * @param source the {@link Source} object.
     */
    void updateSourceAggregateContributions(Source source) throws DatastoreException;

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
     * Queries and returns the {@link AggregateReport}
     * @param aggregateReportId Id of the request Aggregate Report
     * @return the request Aggregate Report; Null in case of SQL failure
     */
    @Nullable
    AggregateReport getAggregateReport(String aggregateReportId)
            throws DatastoreException;

    /**
     * Change the status of an event report to DELIVERED
     *
     * @param eventReportId the id of the event report to be updated
     */
    void markEventReportDelivered(String eventReportId) throws DatastoreException;

    /**
     * Change the status of an aggregate report to DELIVERED
     *
     * @param aggregateReportId the id of the event report to be updated
     */
    void markAggregateReportDelivered(String aggregateReportId) throws DatastoreException;

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
     * Find the number of entries for a rate limit window using the {@link Source} and {@link
     * Trigger}. Rate-Limit Window: (Source Site, Destination Site, Window) from triggerTime.
     *
     * @return the number of entries for the window.
     */
    long getAttributionsPerRateLimitWindow(@NonNull Source source, @NonNull Trigger trigger)
            throws DatastoreException;

    /** Add an entry in Attribution datastore. */
    void insertAttribution(@NonNull Attribution attribution) throws DatastoreException;

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
     * @param start time for deletion range. Set to Instant.MIN to delete everything up to the end
     * @param end time for deletion range. Set to Instant.MAX to delete everything after the start
     * @param origins list of origins which should be used for matching
     * @param domains list of domains which should be used for matching
     * @param matchBehavior {@link DeletionRequest.MatchBehavior} to be used for matching
     * @param deletionMode {@link DeletionRequest.DeletionMode} for selecting data to be deleted
     */
    void deleteMeasurementData(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior,
            @DeletionRequest.DeletionMode int deletionMode)
            throws DatastoreException;

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

    /**
     * Save aggregate encryption key to datastore.
     */
    void insertAggregateEncryptionKey(AggregateEncryptionKey aggregateEncryptionKey)
            throws DatastoreException;

    /**
     * Retrieve all aggregate encryption keys from the datastore whose expiry time is greater than
     * or equal to {@code expiry}.
     */
    List<AggregateEncryptionKey> getNonExpiredAggregateEncryptionKeys(long expiry)
            throws DatastoreException;

    /**
     *  Remove aggregate encryption keys from the datastore older than {@code expiry}.
     */
    void deleteExpiredAggregateEncryptionKeys(long expiry) throws DatastoreException;

    /**
     * Save unencrypted aggregate payload to datastore.
     */
    void insertAggregateReport(AggregateReport payload) throws DatastoreException;

    /**
     * Returns list of all aggregate reports that have a scheduled reporting time in the given
     * window.
     */
    List<String> getPendingAggregateReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException;

    /**
     * Returns list of all pending aggregate reports for a given app right away.
     */
    List<String> getPendingAggregateReportIdsForGivenApp(Uri appName) throws DatastoreException;

    /**
     * Delete all data generated by Measurement API, except for tables in the exclusion list.
     *
     * @param tablesToExclude a {@link List} of tables that won't be deleted. An empty list will
     *     delete every table.
     */
    void deleteAllMeasurementData(List<String> tablesToExclude) throws DatastoreException;

    /**
     * Insert a record into the Async Registration Table.
     *
     * @param asyncRegistration a {@link AsyncRegistration} to insert into the Async Registration
     *     table
     */
    void insertAsyncRegistration(@NonNull AsyncRegistration asyncRegistration)
            throws DatastoreException;

    /**
     * Delete a record from the AsyncRegistration table.
     *
     * @param id a {@link String} id of the record to delete from the AsyncRegistration table.
     */
    void deleteAsyncRegistration(@NonNull String id) throws DatastoreException;

    /**
     * Get the record with the earliest request time and a valid retry count.
     *
     * @param retryLimit a long that is used for determining the next valid record to be serviced
     * @param failedAdTechEnrollmentIds a String that contains the Ids of records that have been
     *     serviced during the current run
     */
    AsyncRegistration fetchNextQueuedAsyncRegistration(
            short retryLimit, List<String> failedAdTechEnrollmentIds) throws DatastoreException;

    /**
     * Update the retry count for a record in the Async Registration table.
     *
     * @param asyncRegistration a {@link AsyncRegistration} for which the retryCount will be updated
     */
    void updateRetryCount(@NonNull AsyncRegistration asyncRegistration) throws DatastoreException;
}
