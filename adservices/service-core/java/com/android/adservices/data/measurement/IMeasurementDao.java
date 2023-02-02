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
import com.android.adservices.service.measurement.reporting.DebugReport;

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
     * Queries and returns the {@link Source}.
     *
     * @param sourceId ID of the requested Source
     * @return the requested Source
     */
    Source getSource(@NonNull String sourceId) throws DatastoreException;

    /**
     * Queries and returns the {@link Trigger}.
     *
     * @param triggerId Id of the request Trigger
     * @return the requested Trigger
     */
    Trigger getTrigger(String triggerId) throws DatastoreException;

    /**
     * Fetches the count of aggregate reports for the provided destination.
     *
     * @param attributionDestination Uri for the destination
     * @param destinationType DestinationType App/Web
     * @return number of aggregate reports in the database attributed to the provided destination
     */
    int getNumAggregateReportsPerDestination(
            @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
            throws DatastoreException;

    /**
     * Fetches the count of event reports for the provided destination.
     *
     * @param attributionDestination Uri for the destination
     * @param destinationType DestinationType App/Web
     * @return number of event reports in the database attributed to the provided destination
     */
    int getNumEventReportsPerDestination(
            @NonNull Uri attributionDestination, @EventSurfaceType int destinationType)
            throws DatastoreException;

    /**
     * Gets the number of sources associated to a publisher.
     *
     * @param publisherUri Uri for the publisher
     * @param publisherType PublisherType App/Web
     * @return Number of sources registered for the given publisher
     */
    long getNumSourcesPerPublisher(Uri publisherUri, @EventSurfaceType int publisherType)
            throws DatastoreException;

    /**
     * Gets the number of triggers a registrant has registered.
     */
    long getNumTriggersPerRegistrant(Uri registrant) throws DatastoreException;

    /** Gets the number of triggers associated to a destination. */
    long getNumTriggersPerDestination(Uri destination, @EventSurfaceType int destinationType)
            throws DatastoreException;

    /**
     * Gets the count of distinct IDs of enrollments in the Attribution table in a time window with
     * matching publisher and destination, excluding a given enrollment ID.
     */
    Integer countDistinctEnrollmentsPerPublisherXDestinationInAttribution(
            Uri sourceSite,
            Uri destination,
            String excludedEnrollmentId,
            long windowStartTime,
            long windowEndTime)
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
     *
     * @param triggerIds trigger to update
     * @param status status to apply
     * @throws DatastoreException database transaction related issues
     */
    void updateTriggerStatus(@NonNull List<String> triggerIds, @Trigger.Status int status)
            throws DatastoreException;

    /**
     * Add an entry to the Source datastore.
     *
     * @param source Source data to be inserted.
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
     * @param sourceIds list of sources.
     * @param status value to be set
     */
    void updateSourceStatus(@NonNull List<String> sourceIds, @Source.Status int status)
            throws DatastoreException;

    /**
     * Update the value of {@link Source.Status} for the corresponding {@link Source}
     *
     * @param source the {@link Source} object.
     */
    void updateSourceEventReportDedupKeys(@NonNull Source source) throws DatastoreException;

    /**
     * Update the set of Aggregate dedup keys contained in the provided {@link Source}
     *
     * @param source the {@link Source} object.
     */
    void updateSourceAggregateReportDedupKeys(@NonNull Source source) throws DatastoreException;

    /**
     * Updates the value of aggregate contributions for the corresponding {@link Source}
     *
     * @param source the {@link Source} object.
     */
    void updateSourceAggregateContributions(@NonNull Source source) throws DatastoreException;

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
     * Queries and returns the {@link DebugReport}
     *
     * @param debugReportId of the request Debug Report
     * @return the request Debug Report; Null in case of SQL failure
     */
    @Nullable
    DebugReport getDebugReport(String debugReportId) throws DatastoreException;

    /**
     * Change the status of an event report to DELIVERED
     *
     * @param eventReportId the id of the event report to be updated
     * @param status status of the event report
     */
    void markEventReportStatus(String eventReportId, @EventReport.Status int status)
            throws DatastoreException;

    /**
     * Change the status of an event debug report to DELIVERED
     *
     * @param eventReportId the id of the event report to be updated
     */
    void markEventDebugReportDelivered(String eventReportId) throws DatastoreException;

    /**
     * Change the status of an aggregate report to DELIVERED
     *
     * @param aggregateReportId the id of the event report to be updated
     * @param status new status to set
     */
    void markAggregateReportStatus(String aggregateReportId, @AggregateReport.Status int status)
            throws DatastoreException;

    /**
     * Change the status of an aggregate debug report to DELIVERED
     *
     * @param aggregateReportId the id of the event report to be updated
     */
    void markAggregateDebugReportDelivered(String aggregateReportId) throws DatastoreException;

    /**
     * Saves the {@link EventReport} to datastore.
     */
    void insertEventReport(EventReport eventReport) throws DatastoreException;

    /**
     * Deletes the {@link EventReport} from datastore.
     */
    void deleteEventReport(EventReport eventReport) throws DatastoreException;

    /** Deletes the {@link DebugReport} from datastore. */
    void deleteDebugReport(String debugReportId) throws DatastoreException;

    /**
     * Returns list of all event reports that have a scheduled reporting time in the given window.
     */
    List<String> getPendingEventReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException;

    /** Returns list of all debug event reports. */
    List<String> getPendingDebugEventReportIds() throws DatastoreException;

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

    /** Deletes all expired records in measurement tables. */
    void deleteExpiredRecords() throws DatastoreException;

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

    /** Save debug report payload to datastore. */
    void insertDebugReport(DebugReport payload) throws DatastoreException;

    /**
     * Returns list of all aggregate reports that have a scheduled reporting time in the given
     * window.
     */
    List<String> getPendingAggregateReportIdsInWindow(long windowStartTime, long windowEndTime)
            throws DatastoreException;

    /** Returns list of all aggregate debug reports. */
    List<String> getPendingAggregateDebugReportIds() throws DatastoreException;

    /** Returns list of all debug reports. */
    List<String> getDebugReportIds() throws DatastoreException;

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
     * Delete records from source table that match provided source IDs.
     *
     * @param sourceIds source IDs to match
     * @throws DatastoreException database transaction issues
     */
    void deleteSources(@NonNull List<String> sourceIds) throws DatastoreException;

    /**
     * Delete records from source table that match provided trigger IDs.
     *
     * @param triggerIds trigger IDs to match
     * @throws DatastoreException database transaction issues
     */
    void deleteTriggers(@NonNull List<String> triggerIds) throws DatastoreException;

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

    /**
     * Deletes all records in measurement tables that correspond with a Uri not in the provided
     * list.
     *
     * @param uriList a {@link List} of Uris whos related records won't be deleted.
     * @throws DatastoreException
     */
    void deleteAppRecordsNotPresent(List<Uri> uriList) throws DatastoreException;

    /**
     * Fetches aggregate reports that match either given source or trigger IDs. If A1 is set of
     * aggregate reports that match any of sourceIds and A2 is set of aggregate reports that match
     * any of triggerIds, then we delete (A1 U A2).
     *
     * @param sourceIds sources to be matched with aggregate reports
     * @param triggerIds triggers to be matched with aggregate reports
     */
    List<AggregateReport> fetchMatchingAggregateReports(
            @NonNull List<String> sourceIds, @NonNull List<String> triggerIds)
            throws DatastoreException;

    /**
     * Fetches event reports that match either given source or trigger IDs. If A1 is set of event
     * reports that match any of sourceIds and A2 is set of event reports that match any of
     * triggerIds, then we delete (A1 U A2).
     *
     * @param sourceIds sources to be matched with event reports
     * @param triggerIds triggers to be matched with event reports
     */
    List<EventReport> fetchMatchingEventReports(
            @NonNull List<String> sourceIds, @NonNull List<String> triggerIds)
            throws DatastoreException;

    /**
     * Returns list of sources matching registrant, publishers and also in the provided time frame.
     * It matches registrant and time range (start & end) irrespective of the {@code matchBehavior}.
     * In the resulting set, if matchBehavior is {@link
     * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_DELETE}, then it
     * matches origins and domains. In case of {@link
     * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_PRESERVE}, it
     * returns the records that don't match origins or domain.
     *
     * @param registrant registrant to match
     * @param start event time should be after this instant (inclusive)
     * @param end event time should be after this instant (inclusive)
     * @param origins publisher site match
     * @param domains publisher top level domain matches
     * @param matchBehavior indicates whether to return matching or inversely matching (everything
     *     except matching) data
     * @return list of source IDs
     * @throws DatastoreException database transaction level issues
     */
    List<String> fetchMatchingSources(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException;

    /**
     * Returns list of triggers matching registrant, publishers and also in the provided time frame.
     * It matches registrant and time range (start & end) irrespective of the {@code matchBehavior}.
     * In the resulting set, if matchBehavior is {@link
     * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_DELETE}, then it
     * matches origins and domains. In case of {@link
     * android.adservices.measurement.DeletionRequest.MatchBehavior#MATCH_BEHAVIOR_PRESERVE}, it
     * returns the records that don't match origins or domain.
     *
     * @param registrant registrant to match
     * @param start trigger time should be after this instant (inclusive)
     * @param end trigger time should be after this instant (inclusive)
     * @param origins destination site match
     * @param domains destination top level domain matches
     * @param matchBehavior indicates whether to return matching or inversely matching (everything
     *     except matching) data
     * @return list of trigger IDs
     * @throws DatastoreException database transaction level issues
     */
    List<String> fetchMatchingTriggers(
            @NonNull Uri registrant,
            @NonNull Instant start,
            @NonNull Instant end,
            @NonNull List<Uri> origins,
            @NonNull List<Uri> domains,
            @DeletionRequest.MatchBehavior int matchBehavior)
            throws DatastoreException;
}
