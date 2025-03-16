/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.service.Flags.MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW;
import static com.android.adservices.service.Flags.MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW;
import static com.android.adservices.service.measurement.registration.SourceEligibilityChecker.InsertSourcePermission;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.TriggerSpecsUtil;
import com.android.adservices.service.measurement.attribution.TriggerContentProvider;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link SourceEligibilityChecker} */
@SpyStatic(FlagsFactory.class)
public class SourceEligibilityCheckerTest extends AdServicesExtendedMockitoTestCase {
    private static final String DEFAULT_ENROLLMENT_ID = "enrollment_id";
    private static final Uri APP_TOP_ORIGIN =
            Uri.parse("android-app://" + sContext.getPackageName());

    private static final Source SOURCE_1 =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setEventId(new UnsignedLong(1L))
                    .setPublisher(APP_TOP_ORIGIN)
                    .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                    .setWebDestinations(List.of(WebUtil.validUri("https://web-destination1.test")))
                    .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                    .setRegistrant(Uri.parse("android-app://com.example"))
                    .setEventTime(new Random().nextLong())
                    .setExpiryTime(8640000010L)
                    .setPriority(100L)
                    .setSourceType(Source.SourceType.EVENT)
                    .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                    .setDebugKey(new UnsignedLong(47823478789L))
                    .build();
    private static final Source NAVIGATION_SOURCE =
            SourceFixture.getMinimalValidSourceBuilder()
                    .setEventId(new UnsignedLong(1L))
                    .setPublisher(APP_TOP_ORIGIN)
                    .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                    .setWebDestinations(List.of(WebUtil.validUri("https://web-destination1.test")))
                    .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                    .setRegistrant(Uri.parse("android-app://com.example"))
                    .setEventTime(new Random().nextLong())
                    .setExpiryTime(8640000010L)
                    .setPriority(100L)
                    .setSourceType(Source.SourceType.NAVIGATION)
                    .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                    .setDebugKey(new UnsignedLong(47823478789L))
                    .build();
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");
    private static final Uri WEB_TOP_ORIGIN = WebUtil.validUri("https://example.test");

    SourceEligibilityChecker mSourceEligibilityChecker;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private ContentResolver mContentResolver;
    @Mock private ContentProviderClient mMockContentProviderClient;
    @Mock private DebugReportApi mDebugReportApi;
    @Mock private AsyncFetchStatus mAsyncFetchStatus;

    @Before
    public void before() throws Exception {
        mocker.mockGetFlags(mMockFlags);
        mSourceEligibilityChecker = new SourceEligibilityChecker(mMockFlags, mDebugReportApi);

        Uri triggerUri = TriggerContentProvider.getTriggerUri();
        when(mContentResolver.acquireContentProviderClient(triggerUri))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(triggerUri);
        when(mMockFlags.getMeasurementMaxSourcesPerPublisher())
                .thenReturn(Flags.MEASUREMENT_MAX_SOURCES_PER_PUBLISHER);
        when(mMockFlags.getMeasurementEnableNavigationReportingOriginCheck())
                .thenReturn(Flags.MEASUREMENT_ENABLE_NAVIGATION_REPORTING_ORIGIN_CHECK);
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE);
        when(mMockFlags.getMeasurementMinReportingOriginUpdateWindow())
                .thenReturn(Flags.MEASUREMENT_MIN_REPORTING_ORIGIN_UPDATE_WINDOW);
        when(mMockFlags.getMeasurementMaxReportingOriginsPerSourceReportingSitePerWindow())
                .thenReturn(MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW);
        when(mMockFlags.getMeasurementMaxDistinctRepOrigPerPublXDestInSource())
                .thenReturn(Flags.MEASUREMENT_MAX_DISTINCT_REP_ORIG_PER_PUBLISHER_X_DEST_IN_SOURCE);
        when(mMockFlags.getMeasurementEnableDestinationRateLimit())
                .thenReturn(Flags.MEASUREMENT_ENABLE_DESTINATION_RATE_LIMIT);
        when(mMockFlags.getMeasurementMaxDestinationsPerPublisherPerRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW);
        when(mMockFlags.getMeasurementMaxDestPerPublisherXEnrollmentPerRateLimitWindow())
                .thenReturn(MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW);
        when(mMockFlags.getMeasurementDestinationRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_DESTINATION_RATE_LIMIT_WINDOW);
        when(mMockFlags.getMeasurementDestinationPerDayRateLimit())
                .thenReturn(Flags.MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT);
        when(mMockFlags.getMeasurementEnableDestinationPerDayRateLimitWindow())
                .thenReturn(Flags.MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW);
        when(mMockFlags.getMeasurementDestinationPerDayRateLimitWindowInMs())
                .thenReturn(Flags.MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW_IN_MS);
        when(mMockFlags.getMeasurementDefaultSourceDestinationLimitAlgorithm())
                .thenReturn(Flags.MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(Flags.MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS);
        when(mMockFlags.getMeasurementRateLimitWindowMilliseconds())
                .thenReturn(Flags.MEASUREMENT_RATE_LIMIT_WINDOW_MILLISECONDS);
    }

    @Test
    public void testIsAllowedToInsert_isAllowed() throws DatastoreException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        new HashSet<>())
                                .isAllowed())
                .isTrue();

        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
    }

    @Test
    public void testIsAllowedToInsert_exceedsDestinationsPrivacyParam_isNotAllowed()
            throws DatastoreException, RemoteException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(1))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsAppGlobalDestinationsPrivacyParam_isNotAllowed()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        anyLong(),
                        anyLong()))
                .thenReturn(500);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        anyLong(),
                        anyLong());
        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.WEB),
                        anyLong(),
                        anyLong());
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        // this check occurs after global destination limit check
        verify(mMeasurementDao, never())
                .countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(), any(), anyInt(), anyString(), anyLong(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceReport(
                        eq(SOURCE_1),
                        eq(DebugReportApi.Type.SOURCE_SUCCESS),
                        eq(null),
                        eq(mMeasurementDao));
        assertThat(adrTypes)
                .containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_GLOBAL_RATE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsWebGlobalDestinationsPrivacyParam_isNotAllowed()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        anyLong(),
                        anyLong()))
                .thenReturn(500);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        anyLong(),
                        anyLong());
        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.WEB),
                        anyLong(),
                        anyLong());
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        anyLong(),
                        anyLong());
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        anyLong(),
                        anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        // this check occurs after global destination limit check
        verify(mMeasurementDao, never())
                .countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(), any(), anyInt(), anyString(), anyLong(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceReport(
                        eq(SOURCE_1),
                        eq(DebugReportApi.Type.SOURCE_SUCCESS),
                        eq(null),
                        eq(mMeasurementDao));
        assertThat(adrTypes)
                .containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_GLOBAL_RATE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsPerDayAppDestinationsPrivacyParam_IsNotAllowed()
            throws DatastoreException {
        when(mMockFlags.getMeasurementEnableDestinationPerDayRateLimitWindow()).thenReturn(true);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        // per minute rate limit
                        eq(SOURCE_1.getEventTime() - TimeUnit.MINUTES.toMillis(1)),
                        eq(SOURCE_1.getEventTime())))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        // per day rate limit
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime())))
                .thenReturn(500);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        // Event if global rate limit fails, per day rate limit failure is reported
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(500);

        Set<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getEventTime() - TimeUnit.MINUTES.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao, never())
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.WEB),
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceDestinationPerDayRateLimitDebugReport(
                        eq(SOURCE_1), eq(String.valueOf(100)), eq(mMeasurementDao));
        assertThat(adrTypes)
                .containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_PER_DAY_RATE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsPerDayWebDestinationsPrivacyParam_IsNotAllowed()
            throws DatastoreException {
        when(mMockFlags.getMeasurementEnableDestinationPerDayRateLimitWindow()).thenReturn(true);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt(),
                        // per minute rate limit
                        eq(SOURCE_1.getEventTime() - TimeUnit.MINUTES.toMillis(1)),
                        eq(SOURCE_1.getEventTime())))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        // per day rate limit
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime())))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.WEB),
                        // per day rate limit
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime())))
                .thenReturn(500);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(500);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getEventTime() - TimeUnit.MINUTES.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.APP),
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        eq(EventSurfaceType.WEB),
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceDestinationPerDayRateLimitDebugReport(
                        eq(SOURCE_1), eq(String.valueOf(100)), eq(mMeasurementDao));
        assertThat(adrTypes)
                .containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_PER_DAY_RATE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_perDayRateLimitDisabledAndIgnored_isAllowed()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt(),
                        // per minute rate limit
                        eq(SOURCE_1.getEventTime() - TimeUnit.MINUTES.toMillis(1)),
                        eq(SOURCE_1.getEventTime())))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isTrue();

        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getEventTime() - TimeUnit.MINUTES.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao, never())
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt(),
                        eq(SOURCE_1.getEventTime() - TimeUnit.DAYS.toMillis(1)),
                        eq(SOURCE_1.getEventTime()));
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        assertThat(adrTypes).isEmpty();
    }

    @Test
    public void testIsAllowedToInsert_exceedsDestinationReportingRateLimit_isNotAllowed()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(500);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceDestinationPerMinuteRateLimitDebugReport(
                        eq(SOURCE_1), eq("200"), eq(mMeasurementDao));
        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_RATE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsOneOriginPerPublisherEnrollmentLimit_isNotAllowed()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(), any(), anyInt(), any(), anyLong(), anyLong()))
                .thenReturn(3);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao)
                .countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(), any(), anyInt(), any(), anyLong(), anyLong());
        // verify global destination rate limit before publisher per enrollment
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), anyList(), anyInt(), anyLong(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceReport(
                        eq(SOURCE_1),
                        eq(DebugReportApi.Type.SOURCE_SUCCESS),
                        eq(null),
                        eq(mMeasurementDao));
        assertThat(adrTypes)
                .containsExactly(DebugReportApi.Type.SOURCE_REPORTING_ORIGIN_PER_SITE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsMaxSourcesLimit_isNotAllowed()
            throws DatastoreException {
        doReturn((long) Flags.MEASUREMENT_MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao).getNumSourcesPerPublisher(any(), anyInt());
        verify(mMeasurementDao, never())
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), anyString(), anyList(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), anyList(), any(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), anyString(), anyList(), anyInt(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), anyString(), anyList(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctDestinationsPerPublisherPerRateLimitWindow(
                        any(), anyInt(), anyList(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(), any(), anyInt(), anyString(), anyLong(), anyLong());
        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_STORAGE_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_exceedsPrivacyParamAdTech_isNotAllowed()
            throws DatastoreException {
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(100));

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();

        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao)
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_REPORTING_ORIGIN_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_flexEventApiValidNav_isAllowed()
            throws DatastoreException, JSONException {
        when(mMockFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 2;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(
                                new TriggerSpecs(triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        testSource,
                                        testSource.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        new HashSet<>())
                                .isAllowed())
                .isTrue();
    }

    @Test
    public void testIsAllowedToInsert_flexLiteApiExceedMaxInfoGain_isAllowed()
            throws DatastoreException {
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(1)
                        .setEventReportWindows("{ 'end_times': [3600]}")
                        .build();

        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        testSource,
                                        testSource.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        new HashSet<>())
                                .isAllowed())
                .isTrue();
    }

    @Test
    public void testIsAllowedToInsert_flexEventApiValidV1NavNearBoundary_isAllowed()
            throws DatastoreException, JSONException {
        when(mMockFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toSeconds(2),
                                TimeUnit.DAYS.toSeconds(7),
                                TimeUnit.DAYS.toSeconds(30))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(
                                new TriggerSpecs(triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        testSource,
                                        testSource.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        new HashSet<>())
                                .isAllowed())
                .isTrue();
    }

    @Test
    public void testIsAllowedToInsert_flexEventApiV1ParamEventNearBoundary_isAllowed()
            throws DatastoreException, JSONException {
        when(mMockFlags.getMeasurementFlexibleEventReportingApiEnabled()).thenReturn(true);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toSeconds(7))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 1;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        // Navigation and Event source has different maximum information gain
                        // threshold
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(
                                new TriggerSpecs(triggerSpecsArray, maxEventLevelReports, null))
                        .build();

        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        testSource,
                                        testSource.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        new HashSet<>())
                                .isAllowed())
                .isTrue();
    }

    @Test
    public void testIsAllowedToInsert_existsNavWithSameReportingOrigin_isNotAllowed()
            throws DatastoreException {
        when(mMockFlags.getMeasurementEnableNavigationReportingOriginCheck()).thenReturn(true);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        NAVIGATION_SOURCE,
                                        NAVIGATION_SOURCE.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isFalse();
        verify(mMeasurementDao).countNavigationSourcesPerReportingOrigin(any(), any());
    }

    @Test
    public void testIsAllowedToInsert_deletesOldestAppDestInLoopFifoInsertion_isAllowed()
            throws DatastoreException {
        Source sourceToInsert = SourceFixture.getValidSourceBuilder().setId("S5").build();
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource()).thenReturn(5);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        // For app destination
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                // The distinct destinations reduce after the deletion through FIFO -
                // 6 - before deletion
                // 5 - after first deletion
                // 4 - verification after deletion
                .thenReturn(6, 5, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, List.of("S1")))
                .thenReturn(new Pair<>(0L, List.of("S2")));

        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                new HashSet<>()))
                .isEqualTo(InsertSourcePermission.ALLOWED_FIFO_SUCCESS);

        // Verification
        ArgumentCaptor<List<String>> updatedStatus = ArgumentCaptor.forClass(List.class);
        List<List<String>> sourcesToDelete = List.of(List.of("S1"), List.of("S2"));
        verify(mMeasurementDao, times(2))
                .updateSourceStatus(updatedStatus.capture(), eq(Source.Status.MARKED_TO_DELETE));
        assertThat(updatedStatus.getAllValues()).containsExactlyElementsIn(sourcesToDelete);

        ArgumentCaptor<List<String>> deletedAggReportSources = ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .deletePendingAggregateReportsAndAttributionsForSources(
                        deletedAggReportSources.capture());
        assertThat(deletedAggReportSources.getAllValues())
                .containsExactlyElementsIn(sourcesToDelete);

        ArgumentCaptor<List<String>> deletedFakeEventReportSources =
                ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .deleteFutureFakeEventReportsForSources(
                        deletedFakeEventReportSources.capture(), eq(sourceToInsert.getEventTime()));
        assertThat(deletedFakeEventReportSources.getAllValues())
                .containsExactlyElementsIn(sourcesToDelete);
        verify(mAsyncFetchStatus, times(2)).incrementNumDeletedEntities(1);
    }

    @Test
    public void testIsAllowedToInsert_deletesOldestWebDestFifoInsertion_isAllowed()
            throws DatastoreException {
        Source sourceToInsert = SourceFixture.getValidSourceBuilder().setId("S5").build();
        List<String> sourceIdsWithLruDestination = List.of("S1", "S2");
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource()).thenReturn(5);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        // For app destination
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, sourceIdsWithLruDestination));

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                adrTypes))
                .isEqualTo(InsertSourcePermission.ALLOWED_FIFO_SUCCESS);

        // Verification
        ArgumentCaptor<List<String>> updatedStatus = ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao)
                .updateSourceStatus(updatedStatus.capture(), eq(Source.Status.MARKED_TO_DELETE));
        assertThat(updatedStatus.getValue()).isEqualTo(sourceIdsWithLruDestination);
        ArgumentCaptor<List<String>> deletedReportSources = ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao)
                .deletePendingAggregateReportsAndAttributionsForSources(
                        deletedReportSources.capture());
        assertThat(deletedReportSources.getValue()).isEqualTo(sourceIdsWithLruDestination);

        ArgumentCaptor<List<String>> deletedFakeEventReportSources =
                ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao)
                .deleteFutureFakeEventReportsForSources(
                        deletedFakeEventReportSources.capture(), eq(sourceToInsert.getEventTime()));
        assertThat(deletedFakeEventReportSources.getValue())
                .containsExactlyElementsIn(sourceIdsWithLruDestination);
        verify(mAsyncFetchStatus).incrementNumDeletedEntities(2);

        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT_REPLACED);
    }

    @Test
    public void testIsAllowedToInsert_deletesOldestAppAndWebDestsFifoInsertion_isAllowed()
            throws DatastoreException {
        Source sourceToInsert = SourceFixture.getValidSourceBuilder().setId("S5").build();
        List<String> appDestSourceIdsWithLruDestination = List.of("S1", "S2");
        List<String> webDestSourceIdsWithLruDestination = List.of("S3", "S4");
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource()).thenReturn(5);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        // For app destination
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, appDestSourceIdsWithLruDestination));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, webDestSourceIdsWithLruDestination));

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                adrTypes))
                .isEqualTo(InsertSourcePermission.ALLOWED_FIFO_SUCCESS);

        // Verification
        ArgumentCaptor<List<String>> updatedStatus = ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .updateSourceStatus(updatedStatus.capture(), eq(Source.Status.MARKED_TO_DELETE));
        List<List<String>> updatedStatusValues = updatedStatus.getAllValues();
        assertThat(updatedStatusValues.get(0)).isEqualTo(appDestSourceIdsWithLruDestination);
        assertThat(updatedStatusValues.get(1)).isEqualTo(webDestSourceIdsWithLruDestination);

        ArgumentCaptor<List<String>> deletedReportSources = ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .deletePendingAggregateReportsAndAttributionsForSources(
                        deletedReportSources.capture());
        List<List<String>> deletedReportSourcesAllValues = deletedReportSources.getAllValues();
        assertThat(deletedReportSourcesAllValues.get(0))
                .isEqualTo(appDestSourceIdsWithLruDestination);
        assertThat(deletedReportSourcesAllValues.get(1))
                .isEqualTo(webDestSourceIdsWithLruDestination);

        ArgumentCaptor<List<String>> deletedFakeEventReportSources =
                ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .deleteFutureFakeEventReportsForSources(
                        deletedFakeEventReportSources.capture(), eq(sourceToInsert.getEventTime()));
        assertThat(deletedFakeEventReportSources.getAllValues().get(0))
                .containsExactlyElementsIn(appDestSourceIdsWithLruDestination);
        assertThat(deletedFakeEventReportSources.getAllValues().get(1))
                .containsExactlyElementsIn(webDestSourceIdsWithLruDestination);
        verify(mAsyncFetchStatus, times(2)).incrementNumDeletedEntities(2);

        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT_REPLACED);
    }

    @Test
    public void testIsAllowedToInsert_deletesOldestDestsNoReportDeletionFifo_isAllowed()
            throws DatastoreException {
        Source sourceToInsert = SourceFixture.getValidSourceBuilder().setId("S5").build();
        List<String> appDestSourceIdsWithLruDestination = List.of("S1", "S2");
        List<String> webDestSourceIdsWithLruDestination = List.of("S3", "S4");
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource()).thenReturn(5);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        // For app destination
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, appDestSourceIdsWithLruDestination));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, webDestSourceIdsWithLruDestination));

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                adrTypes))
                .isEqualTo(InsertSourcePermission.ALLOWED_FIFO_SUCCESS);

        // Verification
        ArgumentCaptor<List<String>> updatedStatus = ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .updateSourceStatus(updatedStatus.capture(), eq(Source.Status.MARKED_TO_DELETE));
        List<List<String>> updatedStatusValues = updatedStatus.getAllValues();
        assertThat(updatedStatusValues.get(0)).isEqualTo(appDestSourceIdsWithLruDestination);
        assertThat(updatedStatusValues.get(1)).isEqualTo(webDestSourceIdsWithLruDestination);

        verify(mMeasurementDao, never())
                .deletePendingAggregateReportsAndAttributionsForSources(any());

        ArgumentCaptor<List<String>> deletedFakeEventReportSources =
                ArgumentCaptor.forClass(List.class);
        verify(mMeasurementDao, times(2))
                .deleteFutureFakeEventReportsForSources(
                        deletedFakeEventReportSources.capture(), eq(sourceToInsert.getEventTime()));
        assertThat(deletedFakeEventReportSources.getAllValues().get(0))
                .containsExactlyElementsIn(appDestSourceIdsWithLruDestination);
        assertThat(deletedFakeEventReportSources.getAllValues().get(1))
                .containsExactlyElementsIn(webDestSourceIdsWithLruDestination);
        verify(mAsyncFetchStatus, times(2)).incrementNumDeletedEntities(2);

        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT_REPLACED);
    }

    @Test
    public void testIsAllowedToInsert_incomingWebDestsAreMoreThanLimit_isNotAllowed()
            throws DatastoreException {
        Source sourceToInsert =
                SourceFixture.getValidSourceBuilder()
                        .setId("S5")
                        .setPublisher(WEB_TOP_ORIGIN)
                        .setWebDestinations(
                                List.of(
                                        Uri.parse("https://www.example1.com"),
                                        Uri.parse("https://www.example2.com"),
                                        Uri.parse("https://www.example3.com"),
                                        Uri.parse("https://www.example4.com")))
                        .setAppDestinations(null)
                        .build();
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        // Destinations are 4 vs the limit is 3
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource()).thenReturn(3);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);

        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.WEB,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                new HashSet<>()))
                .isEqualTo(InsertSourcePermission.NOT_ALLOWED);
    }

    @Test
    public void testIsAllowedToInsert_appDestCountWithinFifoLimitNoDeletion_isAllowed()
            throws DatastoreException {
        Source sourceToInsert =
                SourceFixture.getValidSourceBuilder()
                        .setId("S5")
                        .setAppDestinations(List.of(APP_DESTINATION))
                        .build();
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        // 1 app destination vs the limit = 100
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(10);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                adrTypes))
                .isEqualTo(InsertSourcePermission.ALLOWED);

        // Verification
        verify(mMeasurementDao, never()).updateSourceStatus(anyList(), anyInt());
        verify(mMeasurementDao, never())
                .deletePendingAggregateReportsAndAttributionsForSources(anyList());
        verify(mMeasurementDao, never())
                .deleteFutureFakeEventReportsForSources(anyList(), anyLong());
        verify(mAsyncFetchStatus, never()).incrementNumDeletedEntities(anyInt());
        assertThat(adrTypes).isEmpty();
    }

    @Test
    public void testIsAllowedToInsert_webDestCountWithinFifoLimitNoDeletion_isAllowed()
            throws DatastoreException {
        Source sourceToInsert =
                SourceFixture.getValidSourceBuilder()
                        .setId("S5")
                        .setWebDestinations(
                                List.of(
                                        Uri.parse("https://www.example1.com"),
                                        Uri.parse("https://www.example2.com"),
                                        Uri.parse("https://www.example3.com")))
                        .setAppDestinations(null)
                        .build();
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        // Destinations are 4 vs the limit is 100
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource()).thenReturn(100);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(10);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                adrTypes))
                .isEqualTo(InsertSourcePermission.ALLOWED);

        // Verification
        verify(mMeasurementDao, never()).updateSourceStatus(anyList(), anyInt());
        verify(mMeasurementDao, never())
                .deletePendingAggregateReportsAndAttributionsForSources(anyList());
        verify(mMeasurementDao, never())
                .deleteFutureFakeEventReportsForSources(anyList(), anyLong());
        verify(mAsyncFetchStatus, never()).incrementNumDeletedEntities(anyInt());
        assertThat(adrTypes).isEmpty();
    }

    @Test
    public void testIsAllowedToInsert_newSourceHasLowerDestPriority_isNotAllowed()
            throws DatastoreException {
        Source sourceToInsert =
                SourceFixture.getValidSourceBuilder()
                        .setId("S5")
                        // Lower than the priority of the other sources in DB
                        .setDestinationLimitPriority(10L)
                        .build();
        List<String> appDestSourceIdsWithLruDestination = List.of("S1", "S2");
        List<String> webDestSourceIdsWithLruDestination = List.of("S3", "S4");
        int fifoLimit = 5;
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.countDistinctRegOriginPerPublisherXEnrollmentExclRegOrigin(
                        any(Uri.class),
                        any(Uri.class),
                        anyInt(),
                        anyString(),
                        anyLong(),
                        anyLong()))
                .thenReturn(0);
        when(mMeasurementDao.getNumSourcesPerPublisher(any(), anyInt())).thenReturn(0L);
        when(mMockFlags.getMeasurementEnableFifoDestinationsDeleteAggregateReports())
                .thenReturn(true);
        when(mMockFlags.getMeasurementMaxDistinctDestinationsInActiveSource())
                .thenReturn(fifoLimit);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);
        // For app destination
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getAppDestinations()),
                        eq(EventSurfaceType.APP),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(20L, appDestSourceIdsWithLruDestination));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                // The destinations reduce after the deletion through FIFO -
                // 6 - initial check
                // 4 - verification after deletion
                .thenReturn(6, 4);
        when(mMeasurementDao.fetchSourceIdsForLowestPriorityDestinationXEnrollmentXPublisher(
                        eq(sourceToInsert.getPublisher()),
                        eq(sourceToInsert.getPublisherType()),
                        eq(sourceToInsert.getEnrollmentId()),
                        eq(sourceToInsert.getWebDestinations()),
                        eq(EventSurfaceType.WEB),
                        eq(sourceToInsert.getEventTime())))
                .thenReturn(new Pair<>(0L, webDestSourceIdsWithLruDestination));

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker.isAllowedToInsert(
                                sourceToInsert,
                                sourceToInsert.getPublisher(),
                                EventSurfaceType.APP,
                                mMeasurementDao,
                                mAsyncFetchStatus,
                                adrTypes))
                .isEqualTo(InsertSourcePermission.NOT_ALLOWED);

        // Verification
        verify(mMeasurementDao, never()).updateSourceStatus(anyCollection(), anyInt());
        verify(mMeasurementDao, never())
                .deletePendingAggregateReportsAndAttributionsForSources(anyList());
        verify(mMeasurementDao, never())
                .deleteFutureFakeEventReportsForSources(anyList(), anyLong());
        verify(mDebugReportApi)
                .scheduleSourceDestinationLimitDebugReport(
                        eq(sourceToInsert), eq(String.valueOf(fifoLimit)), any());
        verify(mAsyncFetchStatus, never()).incrementNumDeletedEntities(anyInt());
        assertThat(adrTypes).containsExactly(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT);
    }

    @Test
    public void testIsAllowedToInsert_existsEventWithSameReportingOrigin_isAllowed()
            throws DatastoreException {
        when(mMockFlags.getMeasurementEnableNavigationReportingOriginCheck()).thenReturn(true);
        when(mMeasurementDao.countNavigationSourcesPerReportingOrigin(any(), any())).thenReturn(1L);

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        SOURCE_1,
                                        SOURCE_1.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isTrue();
        verify(mMeasurementDao, never()).countNavigationSourcesPerReportingOrigin(any(), any());
    }

    @Test
    public void testIsAllowedToInsert_maxEventStatesValid_isAllowed() throws DatastoreException {
        when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
        when(mMockFlags.getMeasurementAttributionScopeMaxInfoGainNavigation())
                .thenReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION);
        when(mMockFlags.getMeasurementAttributionScopeMaxInfoGainEvent())
                .thenReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setAttributionScopeLimit(3L)
                        // num trigger states = 5
                        .setMaxEventStates(10L)
                        .build();

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        source,
                                        source.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isTrue();
        verify(mMeasurementDao)
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao)
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao)
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        assertThat(adrTypes).isEmpty();
    }

    @Test
    public void testIsAllowedToInsert_navDualDestValidInfoGain_isAllowed()
            throws DatastoreException {
        when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainDualDestinationNavigation())
                .thenReturn(14.5f);
        when(mMockFlags.getMeasurementAttributionScopeMaxInfoGainDualDestinationNavigation())
                .thenReturn(14.5f);
        when(mMockFlags.getMeasurementAttributionScopeMaxInfoGainDualDestinationEvent())
                .thenReturn(
                        Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_DUAL_DESTINATION_EVENT);
        when(mMeasurementDao.countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setPublisher(APP_TOP_ORIGIN)
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setEnrollmentId(DEFAULT_ENROLLMENT_ID)
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(8000000000L)
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        // Total number of states is 20855 and attribution information gain:
                        // 14.3481.
                        .setAttributionScopeLimit(4L)
                        .setMaxEventStates(10L)
                        .build();

        HashSet<DebugReportApi.Type> adrTypes = new HashSet<>();
        assertThat(
                        mSourceEligibilityChecker
                                .isAllowedToInsert(
                                        source,
                                        source.getPublisher(),
                                        EventSurfaceType.APP,
                                        mMeasurementDao,
                                        mAsyncFetchStatus,
                                        adrTypes)
                                .isAllowed())
                .isTrue();

        // Assertions
        verify(mMeasurementDao, times(2))
                .countDistinctDestPerPubXEnrollmentInUnexpiredSourceInWindow(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPubXEnrollmentInUnexpiredSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong());
        verify(mMeasurementDao, times(2))
                .countDistinctReportingOriginsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        assertThat(adrTypes).isEmpty();
    }
}
