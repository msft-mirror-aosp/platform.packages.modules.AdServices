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

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;
import static android.view.MotionEvent.obtain;

import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_ATTRIBUTION_FILTERS;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_REDIRECTS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_VALUES_PER_ATTRIBUTION_FILTER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.content.Context;
import android.net.Uri;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.WebUtil;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementRegistrationResponseStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.net.ssl.HttpsURLConnection;
/** Unit tests for {@link AsyncSourceFetcher} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class AsyncSourceFetcherTest {
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String ANDROID_APP_SCHEME_URI_PREFIX = ANDROID_APP_SCHEME + "://";
    private static final String DEFAULT_REGISTRATION = WebUtil.validUrl("https://foo.test");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final EnrollmentData ENROLLMENT =
            new EnrollmentData.Builder().setEnrollmentId("enrollment-id").build();
    private static final String DEFAULT_TOP_ORIGIN =
            "https://com.android.adservices.servicecoretest";
    ;
    private static final String DEFAULT_DESTINATION = "android-app://com.myapps";
    private static final String DEFAULT_DESTINATION_WITHOUT_SCHEME = "com.myapps";
    private static final long DEFAULT_PRIORITY = 123;
    private static final long DEFAULT_EXPIRY = 456789;
    private static final long DEFAULT_EXPIRY_ROUNDED = 432000;
    private static final UnsignedLong DEFAULT_EVENT_ID = new UnsignedLong(987654321L);
    private static final UnsignedLong EVENT_ID_1 = new UnsignedLong(987654321L);
    private static final UnsignedLong DEBUG_KEY = new UnsignedLong(823523783L);
    private static final String LIST_TYPE_REDIRECT_URI = WebUtil.validUrl("https://bar.test");
    private static final String LOCATION_TYPE_REDIRECT_URI =
            WebUtil.validUrl("https://example.test");
    private static final String ALT_DESTINATION = "android-app://com.yourapps";
    private static final long ALT_PRIORITY = 321;
    private static final long ALT_EVENT_ID = 123456789;
    private static final long ALT_EXPIRY = 456790;
    private static final Uri REGISTRATION_URI_1 = WebUtil.validUri("https://foo.test");
    private static final Uri REGISTRATION_URI_2 = WebUtil.validUri("https://foo2.test");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os-destination");
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_ID = "12345678901234567890123456";
    private static final String LONG_AGGREGATE_KEY_PIECE = "0x123456789012345678901234567890123";
    private static final Uri OS_DESTINATION_WITH_PATH =
            Uri.parse("android-app://com.os-destination/my/path");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination.test");
    private static final Uri WEB_DESTINATION_WITH_SUBDOMAIN =
            WebUtil.validUri("https://subdomain.web-destination.test");
    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final String SHARED_AGGREGATION_KEYS =
            "[\"GoogleCampaignCounts\",\"GoogleGeo\"]";

    private static final String DEBUG_JOIN_KEY = "SAMPLE_DEBUG_JOIN_KEY";

    AsyncSourceFetcher mFetcher;

    @Mock HttpsURLConnection mUrlConnection;
    @Mock EnrollmentDao mEnrollmentDao;
    @Mock Flags mFlags;
    @Mock AdServicesLogger mLogger;

    private MockitoSession mStaticMockSession;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mFetcher = spy(new AsyncSourceFetcher(mEnrollmentDao, mFlags, mLogger));
        // For convenience, return the same enrollment-ID since we're using many arbitrary
        // registration URIs and not yet enforcing uniqueness of enrollment.
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(ENROLLMENT);
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(SourceFixture.ValidSourceParams.ENROLLMENT_ID);
    }

    @After
    public void cleanup() throws InterruptedException {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testBasicSourceRequest() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(5000L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"shared_aggregation_keys\": "
                                                + SHARED_AGGREGATION_KEYS
                                                + "}\n")));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(true);

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(SHARED_AGGREGATION_KEYS, result.getSharedAggregationKeys());
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                                253)
                                        .setAdTechDomain(null)
                                        .build()));
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_failsWhenNotEnrolled() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.INVALID_ENROLLMENT, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void testSourceRequestWithPostInstallAttributes() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"432000\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "  \"install_attribution_window\": \"272800\",\n"
                                            + "  \"post_install_exclusivity_window\": \"987654\"\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(TimeUnit.SECONDS.toMillis(272800), result.getInstallAttributionWindow());
        assertEquals(TimeUnit.SECONDS.toMillis(987654L), result.getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributesReceivedAsNull() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"432000\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"install_attribution_window\": null,\n"
                                                + "  \"post_install_exclusivity_window\": null\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        // fallback to default value - 30 days
        assertEquals(TimeUnit.SECONDS.toMillis(2592000L), result.getInstallAttributionWindow());
        // fallback to default value - 0 days
        assertEquals(0L, result.getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithInstallAttributesOutofBounds() throws IOException {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"432000\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            // Min value of attribution is 1 day or 86400
                                            // seconds
                                            + "  \"install_attribution_window\": \"86300\",\n"
                                            // Max value of cooldown is 30 days or 2592000
                                            // seconds
                                            + "  \"post_install_exclusivity_window\":"
                                            + " \"9876543210\"\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        // Adjusted to minimum allowed value
        assertEquals(TimeUnit.SECONDS.toMillis(86400), result.getInstallAttributionWindow());
        // Adjusted to maximum allowed value
        assertEquals(TimeUnit.SECONDS.toMillis((2592000L)), result.getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceUrl() {
        RegistrationRequest request = buildRequest(WebUtil.validUrl("bad-schema://foo.test"));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceConnection() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher)
                .openUrl(new URL(DEFAULT_REGISTRATION));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
    }

    @Test
    public void testBadSourceJson_missingSourceEventId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceJson_missingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Collections.emptyMap());
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceJson_missingDestination() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\"")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiry_tooEarly_setToMin() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"86399\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiry_tooLate_setToMax() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"2592001\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiryCloserToUpperBound_roundsUp() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\""
                                        + String.valueOf(TimeUnit.DAYS.toSeconds(1)
                                                + TimeUnit.DAYS.toSeconds(1) / 2L) + "\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_expiryCloserToLowerBound_roundsDown() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\""
                                        + String.valueOf(TimeUnit.DAYS.toSeconds(1)
                                                + TimeUnit.DAYS.toSeconds(1) / 2L - 1L) + "\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_navigationType_doesNotRoundExpiry() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, getInputEvent());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\""
                                        + String.valueOf(TimeUnit.HOURS.toSeconds(25)) + "\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.HOURS.toMillis(25);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_defaultToExpiry() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_lessThanExpiry_setAsIs() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"86400\","
                                        + "\"aggregatable_report_window\":\"86400\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
    }

    @Test
    public void sourceRequest_reportWindows_lessThanExpiry_tooEarly_setToMin() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"2000\","
                                        + "\"aggregatable_report_window\":\"1728\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_greaterThanExpiry_setToExpiry() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"172801\","
                                        + "\"aggregatable_report_window\":\"172801\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(2),
                result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(2),
                result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void sourceRequest_reportWindows_greaterThanExpiry_tooLate_setToExpiry()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + DEFAULT_EVENT_ID + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"2592001\","
                                        + "\"aggregatable_report_window\":\"2592001\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(2),
                result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(2),
                result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchSource_sourceEventIdNegative_fetchSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"-35\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_sourceEventIdTooLarge_fetchSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551616\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_sourceEventIdNotAnInt_fetchSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\"8l2\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchSource_xnaDisabled_nullSharedAggregationKeys() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"shared_aggregation_keys\": "
                                                + SHARED_AGGREGATION_KEYS
                                                + "}\n")));
        when(mFlags.getMeasurementEnableXNA()).thenReturn(false);

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertNull(result.getSharedAggregationKeys());
    }

    @Test
    public void testBasicSourceRequest_sourceEventId_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\",\"source_event_id\":\""
                                                + "18446744073709551615\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(0, result.getPriority());
        assertEquals(new UnsignedLong(-1L), result.getEventId());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_negative() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"-18\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_tooLarge() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"18446744073709551616\"}")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_notAnInt() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"987fs\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertNull(result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequest_debugKey_uses64thBit() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_key\":\"18446744073709551615\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(new UnsignedLong(-1L), result.getDebugKey());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFieldsAndRestNull() throws Exception {
        RegistrationRequest request =
                buildDefaultRegistrationRequestBuilder(DEFAULT_REGISTRATION).build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \"android-app://com.myapps\",\n"
                                                + "\"source_event_id\": \"123\",\n"
                                                + "\"priority\": null,\n"
                                                + "\"expiry\": null\n"
                                                + "}\n")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(new UnsignedLong(123L), result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryLessThan2Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": 1"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryMoreThan30Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "\"expiry\": 2678400"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(0, result.getPriority());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":\"true\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertTrue(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithInvalidDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":\"invalid\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithNullDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":\"null\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithInvalidNoQuotesDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\","
                                                + "\"debug_reporting\":null}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithoutDebugReportingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\""
                                                + DEFAULT_DESTINATION
                                                + "\","
                                                + "\"source_event_id\":\""
                                                + DEFAULT_EVENT_ID
                                                + "\"}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertFalse(result.isDebugReporting());
        assertEquals(
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = buildRequest(WebUtil.validUrl("http://foo.test"));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void test500_ignoreFailure() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(500);
        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));
        when(mUrlConnection.getHeaderFields()).thenReturn(headersSecondRequest);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(
                AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testFailedParsingButValidRedirect_returnFailure() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{}"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(LIST_TYPE_REDIRECT_URI));
        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));
        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectDifferentDestination_keepAllReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + DEFAULT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": "
                                + DEFAULT_EXPIRY
                                + ""
                                + "}\n"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(LIST_TYPE_REDIRECT_URI));
        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + ALT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + ALT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + ALT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": "
                                + ALT_EXPIRY
                                + ""
                                + "}\n"));
        when(mUrlConnection.getHeaderFields())
                .thenReturn(headersFirstRequest)
                .thenReturn(headersSecondRequest);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(LIST_TYPE_REDIRECT_URI, asyncRedirect.getRedirects().get(0).toString());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    // Tests for redirect types

    @Test
    public void testRedirectType_bothRedirectHeaderTypes_choosesListType() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate both 'list' and 'location' type headers
        headers.put("Attribution-Reporting-Redirect", List.of(LIST_TYPE_REDIRECT_URI));
        headers.put("Location", List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);

        // Assert 'none' type redirects were chosen
        assertEquals(AsyncRegistration.RedirectType.NONE, asyncRedirect.getRedirectType());
        assertEquals(1, asyncRedirect.getRedirects().size());
        assertEquals(LIST_TYPE_REDIRECT_URI, asyncRedirect.getRedirects().get(0).toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectHeaderType_choosesLocationType() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only 'location' type header
        headers.put("Location", List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);

        // Assert 'location' type redirects were chosen
        assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncRedirect.getRedirectType());
        assertEquals(1, asyncRedirect.getRedirects().size());
        assertEquals(LOCATION_TYPE_REDIRECT_URI, asyncRedirect.getRedirects().get(0).toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectType_maxCount_noRedirectReturned()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only 'location' type header
        headers.put("Location", List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch = mFetcher.fetchSource(
                appSourceRegistrationRequest(
                        request,
                        AsyncRegistration.RedirectType.DAISY_CHAIN,
                        MAX_REDIRECTS_PER_REGISTRATION),
                asyncFetchStatus,
                asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);

        // Assert 'location' type redirect but no uri
        assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncRedirect.getRedirectType());
        assertEquals(0, asyncRedirect.getRedirects().size());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectType_count1_redirectReturned() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate only 'location' type header
        headers.put("Location", List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(
                                request, AsyncRegistration.RedirectType.DAISY_CHAIN, 1),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);

        // Assert 'location' type redirect and uri
        assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncRedirect.getRedirectType());
        assertEquals(1, asyncRedirect.getRedirects().size());
        assertEquals(LOCATION_TYPE_REDIRECT_URI, asyncRedirect.getRedirects().get(0).toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectType_locationRedirectType_ignoresListType() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(302);
        Map<String, List<String>> headers = getDefaultHeaders();

        // Populate both 'list' and 'location' type headers
        headers.put("Attribution-Reporting-Redirect", List.of(LIST_TYPE_REDIRECT_URI));
        headers.put("Location", List.of(LOCATION_TYPE_REDIRECT_URI));

        when(mUrlConnection.getHeaderFields()).thenReturn(headers);
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(
                                request, AsyncRegistration.RedirectType.DAISY_CHAIN, 1),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertDefaultSourceRegistration(result);

        // Assert 'location' type redirect and uri
        assertEquals(AsyncRegistration.RedirectType.DAISY_CHAIN, asyncRedirect.getRedirectType());
        assertEquals(1, asyncRedirect.getRedirects().size());
        assertEquals(LOCATION_TYPE_REDIRECT_URI, asyncRedirect.getRedirects().get(0).toString());

        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    // End tests for redirect types

    @Test
    public void testBasicSourceRequestWithFilterData() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"432000\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals("android-app://com.myapps", result.getAppDestinations().get(0).toString());
        assertEquals(123, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(432000), result.getExpiryTime());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                "{\"product\":[\"1234\",\"2345\"],\"ctid\":[\"id\"]}",
                result.getFilterDataString());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequest_filterData_invalidJson() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_tooManyFilters() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        StringBuilder filters = new StringBuilder("{");
        filters.append(
                IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                        .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                        .collect(Collectors.joining(",")));
        filters.append("}");
        String filterData = "  \"filter_data\": " + filters + "\n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_keyTooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \""
                        + LONG_FILTER_STRING
                        + "\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_tooManyValues() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        StringBuilder filters =
                new StringBuilder(
                        "{"
                                + "\"filter-string-1\": [\"filter-value-1\"],"
                                + "\"filter-string-2\": [");
        filters.append(
                IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                        .mapToObj(i -> "\"filter-value-" + i + "\"")
                        .collect(Collectors.joining(",")));
        filters.append("]}");
        String filterData = "  \"filter_data\": " + filters + " \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequest_filterData_valueTooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\""
                        + LONG_FILTER_STRING
                        + "\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + filterData
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of(
                        "Attribution-Reporting-Redirect", List.of(LIST_TYPE_REDIRECT_URI)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithAggregateSource() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": {\"campaignCounts\" :"
                                            + " \"0x159\", \"geoValue\" : \"0x5\"}\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(
                new JSONObject("{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}")
                        .toString(),
                result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithAggregateSource_rejectsTooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        for (int i = 0; i < 51; i++) {
            tooManyKeys.append(String.format("\"campaign-%1$s\": \"0x15%1$s\"", i));
        }
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\":"
                                            + " \"987654321\",\"aggregation_keys\": "
                                            + tooManyKeys)));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithAggregateSource_tooManyKeys() throws Exception {
        StringBuilder tooManyKeys = new StringBuilder("{");
        for (int i = 0; i < MAX_AGGREGATE_KEYS_PER_REGISTRATION + 1; i++) {
            tooManyKeys.append(String.format("\"campaign-%1$s\": \"0x15%1$s\"", i));
        }
        tooManyKeys.append("}");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\":"
                                            + " \"987654321\",\"aggregation_keys\": "
                                            + tooManyKeys)));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_keyIsNotAnObject() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": [\"campaignCounts\","
                                            + " \"0x159\", \"geoValue\", \"0x5\"]\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": {\""
                                            + LONG_AGGREGATE_KEY_ID
                                            + "\": \"0x159\","
                                            + "\"geoValue\": \"0x5\"}\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyPiece_missingPrefix()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": {\"campaignCounts\" :"
                                            + " \"0159\", \"geoValue\" : \"0x5\"}\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void testSourceRequestWithAggregateSource_invalidKeyPiece_tooLong() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                            + "  \"destination\": \"android-app://com.myapps\",\n"
                                            + "  \"priority\": \"123\",\n"
                                            + "  \"expiry\": \"456789\",\n"
                                            + "  \"source_event_id\": \"987654321\",\n"
                                            + "\"aggregation_keys\": {\"campaignCounts\":"
                                            + " \"0x159\", \"geoValue\": \""
                                            + LONG_AGGREGATE_KEY_PIECE
                                            + "\"}\n"
                                            + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);
        // Assertion
        assertFalse(fetch.isPresent());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_basic_success() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSource_expiry_tooEarly_setToMin() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"2000\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_expiry_tooLate_setToMax() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"2592001\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_expiryCloserToUpperBound_roundsUp() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\""
                                        + String.valueOf(TimeUnit.DAYS.toSeconds(1)
                                                + TimeUnit.DAYS.toSeconds(1) / 2L) + "\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true, Source.SourceType.EVENT),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_expiryCloserToLowerBound_roundsDown() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\""
                                        + String.valueOf(TimeUnit.DAYS.toSeconds(1)
                                                + TimeUnit.DAYS.toSeconds(1) / 2L - 1L) + "\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true, Source.SourceType.EVENT),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(1);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_navigationType_doesNotRoundExpiry() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\""
                                        + String.valueOf(TimeUnit.HOURS.toSeconds(25)) + "\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true, Source.SourceType.NAVIGATION),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.HOURS.toMillis(25);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_reportWindows_defaultToExpiry() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"172800\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_reportWindows_lessThanExpiry_setAsIs() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"86400\","
                                        + "\"aggregatable_report_window\":\"86400\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_reportWindows_lessThanExpiry_tooEarly_setToMin() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"2000\","
                                        + "\"aggregatable_report_window\":\"1728\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(result.getEventTime() + TimeUnit.DAYS.toMillis(2), result.getExpiryTime());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getEventReportWindow());
        assertEquals(
                result.getEventTime() + TimeUnit.DAYS.toMillis(1),
                result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_reportWindows_greaterThanExpiry_setToExpiry() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"172801\","
                                        + "\"aggregatable_report_window\":\"172801\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSource_reportWindows_greaterThanExpiry_tooLate_setToExpiry()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\"destination\":\"" + DEFAULT_DESTINATION + "\","
                                        + "\"source_event_id\":\"" + EVENT_ID_1 + "\","
                                        + "\"expiry\":\"172800\","
                                        + "\"event_report_window\":\"2592001\","
                                        + "\"aggregatable_report_window\":\"2592001\""
                                        + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        long expiry = result.getEventTime() + TimeUnit.DAYS.toMillis(2);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
    }

    @Test
    public void fetchWebSources_withValidation_success() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(
                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSourcesSuccessWithoutArDebugPermission() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, false),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(
                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertNull(result.getFilterDataString());
        assertNull(result.getAggregateSource());
    }

    @Test
    public void fetchWebSources_oneSuccessAndOneFailure_resultsIntoOneSourceFetched()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        // Its validation will fail due to destination mismatch
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                /* wrong destination */
                                                + "android-app://com.wrongapp"
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withExtendedHeaders_success() throws IOException, JSONException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregateSource = "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}";
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + ", "
                                                + " \"aggregation_keys\": "
                                                + aggregateSource
                                                + "}")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertEquals(filterData, result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L),
                result.getExpiryTime());
        assertEquals(new JSONObject(aggregateSource).toString(), result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withRedirects_ignoresRedirects() throws IOException, JSONException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String aggregateSource = "{\"campaignCounts\" : \"0x159\", \"geoValue\" : \"0x5\"}";
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + " , "
                                                + " \"aggregation_keys\": "
                                                + aggregateSource
                                                + "}"),
                                "Attribution-Reporting-Redirect",
                                List.of(LIST_TYPE_REDIRECT_URI),
                                "Location",
                                List.of(LOCATION_TYPE_REDIRECT_URI)));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(AsyncRegistration.RedirectType.NONE, asyncRedirect.getRedirectType());
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(filterData, result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L),
                result.getExpiryTime());
        assertEquals(new JSONObject(aggregateSource).toString(), result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_appDestinationDoNotMatch_failsDropsSource() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\":"
                                                + " android-app://wrong.os-destination,\n"
                                                + "  \"web_destination\": "
                                                + "\""
                                                + WEB_DESTINATION
                                                + "\""
                                                + ",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_withDebugJoinKey_getsParsed() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_join_key\": \""
                                                + DEBUG_JOIN_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDebugJoinKeyEnrollmentNotAllowListed_joinKeyDropped()
            throws IOException {
        // Setup
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Arrays.asList(SOURCE_REGISTRATION_1), DEFAULT_TOP_ORIGIN, null, null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\",\n"
                                                + "  \"debug_join_key\": \""
                                                + DEBUG_JOIN_KEY
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertEquals(EVENT_ID_1, result.getEventId());
        long expiry =
                result.getEventTime()
                        + TimeUnit.SECONDS.toMillis(
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        assertEquals(expiry, result.getExpiryTime());
        assertEquals(expiry, result.getEventReportWindow());
        assertEquals(expiry, result.getAggregatableReportWindow());
        assertNull(result.getDebugJoinKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_webDestinationDoNotMatch_failsDropsSource() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        String filterData = "{\"product\":[\"1234\",\"2345\"]," + "\"ctid\":[\"id\"]}";
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\":  "
                                                + "\""
                                                + OS_DESTINATION
                                                + "\""
                                                + ",\n"
                                                + "  \"web_destination\": "
                                                + "\""
                                                + WebUtil.validUrl(
                                                        "https://wrong-web-destination.test")
                                                + "\",\n"
                                                + "  \"filter_data\": "
                                                + filterData
                                                + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
        verify(mFetcher, times(1)).openUrl(any());
    }

    @Test
    public void fetchWebSources_osAndWebDestinationMatch_recordSourceSuccess() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION
                                                + "\""
                                                + "}")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L),
                result.getExpiryTime());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_extractsTopPrivateDomain() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION_WITH_SUBDOMAIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \"android-app://com"
                                                + ".myapps\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION
                                                + "\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION_WITH_SUBDOMAIN
                                                + "\""
                                                + "}")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L), result.getExpiryTime());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_extractsDestinationBaseUri() throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION_WITH_PATH,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + OS_DESTINATION_WITH_PATH
                                                + "\",\n"
                                                + "  \"priority\": \"123\",\n"
                                                + "  \"expiry\": \"456789\",\n"
                                                + "  \"source_event_id\": \"987654321\",\n"
                                                + "\"web_destination\": \""
                                                + WEB_DESTINATION
                                                + "\""
                                                + "}")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(OS_DESTINATION, result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(new UnsignedLong(987654321L), result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(456789L), result.getExpiryTime());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_missingDestinations_dropsSource() throws Exception {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        OS_DESTINATION,
                        WEB_DESTINATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDestinationUriNotHavingScheme_attachesAppScheme()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION_WITHOUT_SCHEME),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(0, asyncRedirect.getRedirects().size());
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(Uri.parse(DEFAULT_DESTINATION), result.getAppDestinations().get(0));
        assertNull(result.getFilterDataString());
        assertEquals(EVENT_ID_1, result.getEventId());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(
                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                result.getExpiryTime());
        assertNull(result.getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void fetchWebSources_withDestinationUriHavingHttpsScheme_dropsSource()
            throws IOException {
        // Setup
        WebSourceRegistrationRequest request =
                buildWebSourceRegistrationRequest(
                        Collections.singletonList(SOURCE_REGISTRATION_1),
                        DEFAULT_TOP_ORIGIN,
                        Uri.parse(DEFAULT_DESTINATION_WITHOUT_SCHEME),
                        null);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(REGISTRATION_URI_1.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                // Invalid (https) URI for app destination
                                                + WEB_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + EVENT_ID_1
                                                + "\"\n"
                                                + "}\n")));
        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        webSourceRegistrationRequest(request, true),
                        asyncFetchStatus,
                        asyncRedirect);
        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.PARSING_ERROR, asyncFetchStatus.getStatus());
        assertFalse(fetch.isPresent());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void basicSourceRequest_headersMoreThanMaxResponseSize_emitsMetricsWithAdTechDomain()
            throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        doReturn(5L).when(mFlags).getMaxResponseBasedRegistrationPayloadSizeBytes();
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "\"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "\"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\"\n"
                                                + "}\n")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();
        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        assertTrue(fetch.isPresent());
        verify(mLogger)
                .logMeasurementRegistrationsResponseSize(
                        eq(
                                new MeasurementRegistrationResponseStats.Builder(
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS,
                                                AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                                                115)
                                        .setAdTechDomain(DEFAULT_REGISTRATION)
                                        .build()));
    }

    @Test
    public void fetchSource_withDebugJoinKey_getsParsed() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"debug_join_key\": "
                                                + DEBUG_JOIN_KEY
                                                + "}\n")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertEquals(DEBUG_JOIN_KEY, result.getDebugJoinKey());
    }

    @Test
    public void fetchSource_withDebugJoinKeyEnrollmentNotAllowListed_joinKeyDropped()
            throws Exception {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Source",
                                List.of(
                                        "{\n"
                                                + "  \"destination\": \""
                                                + DEFAULT_DESTINATION
                                                + "\",\n"
                                                + "  \"priority\": \""
                                                + DEFAULT_PRIORITY
                                                + "\",\n"
                                                + "  \"expiry\": \""
                                                + DEFAULT_EXPIRY
                                                + "\",\n"
                                                + "  \"source_event_id\": \""
                                                + DEFAULT_EVENT_ID
                                                + "\",\n"
                                                + "  \"debug_key\": \""
                                                + DEBUG_KEY
                                                + "\","
                                                + "\"debug_join_key\": "
                                                + DEBUG_JOIN_KEY
                                                + "}\n")));

        AsyncRedirect asyncRedirect = new AsyncRedirect();
        AsyncFetchStatus asyncFetchStatus = new AsyncFetchStatus();

        // Execution
        Optional<Source> fetch =
                mFetcher.fetchSource(
                        appSourceRegistrationRequest(request), asyncFetchStatus, asyncRedirect);

        // Assertion
        assertEquals(AsyncFetchStatus.ResponseStatus.SUCCESS, asyncFetchStatus.getStatus());
        assertTrue(fetch.isPresent());
        Source result = fetch.get();
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEBUG_KEY, result.getDebugKey());
        assertNull(result.getDebugJoinKey());
    }

    private RegistrationRequest buildRequest(String registrationUri) {
        return buildRequest(registrationUri, null);
    }

    private RegistrationRequest buildRequest(String registrationUri, InputEvent inputEvent) {
        RegistrationRequest.Builder builder =
                buildDefaultRegistrationRequestBuilder(registrationUri);
        return builder.setInputEvent(inputEvent).build();
    }

    public static AsyncRegistration appSourceRegistrationRequest(
            RegistrationRequest registrationRequest) {
        return appSourceRegistrationRequest(
                registrationRequest, AsyncRegistration.RedirectType.ANY, 0);
    }

    public static AsyncRegistration appSourceRegistrationRequest(
            RegistrationRequest registrationRequest,
            @AsyncRegistration.RedirectType int redirectType,
            int redirectCount) {
        // Necessary for testing
        String enrollmentId = "";
        if (EnrollmentDao.getInstance(sContext)
                        .getEnrollmentDataFromMeasurementUrl(
                                registrationRequest
                                        .getRegistrationUri()
                                        .buildUpon()
                                        .clearQuery()
                                        .build())
                != null) {
            enrollmentId =
                    EnrollmentDao.getInstance(sContext)
                            .getEnrollmentDataFromMeasurementUrl(
                                    registrationRequest
                                            .getRegistrationUri()
                                            .buildUpon()
                                            .clearQuery()
                                            .build())
                            .getEnrollmentId();
        }
        return createAsyncRegistration(
                UUID.randomUUID().toString(),
                enrollmentId,
                registrationRequest.getRegistrationUri(),
                null,
                redirectType == AsyncRegistration.RedirectType.DAISY_CHAIN
                        ? Uri.parse(DEFAULT_DESTINATION)
                        : null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                null,
                Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                        ? AsyncRegistration.RegistrationType.APP_SOURCE
                        : AsyncRegistration.RegistrationType.APP_TRIGGER,
                getSourceType(registrationRequest.getInputEvent()),
                System.currentTimeMillis(),
                0,
                System.currentTimeMillis(),
                redirectType,
                redirectCount,
                false);
    }

    private static AsyncRegistration webSourceRegistrationRequest(
            WebSourceRegistrationRequest webSourceRegistrationRequest, boolean arDebugPermission) {
        return webSourceRegistrationRequest(
                webSourceRegistrationRequest, arDebugPermission, Source.SourceType.NAVIGATION);
    }

    private static AsyncRegistration webSourceRegistrationRequest(
            WebSourceRegistrationRequest webSourceRegistrationRequest,
            boolean arDebugPermission,
            Source.SourceType sourceType) {
        if (webSourceRegistrationRequest.getSourceParams().size() > 0) {
            WebSourceParams webSourceParams = webSourceRegistrationRequest.getSourceParams().get(0);
            // Necessary for testing
            String enrollmentId = "";
            if (EnrollmentDao.getInstance(sContext)
                            .getEnrollmentDataFromMeasurementUrl(
                                    webSourceRegistrationRequest
                                            .getSourceParams()
                                            .get(0)
                                            .getRegistrationUri()
                                            .buildUpon()
                                            .clearQuery()
                                            .build())
                    != null) {
                enrollmentId =
                        EnrollmentDao.getInstance(sContext)
                                .getEnrollmentDataFromMeasurementUrl(
                                        webSourceParams
                                                .getRegistrationUri()
                                                .buildUpon()
                                                .clearQuery()
                                                .build())
                                .getEnrollmentId();
            }
            return createAsyncRegistration(
                    UUID.randomUUID().toString(),
                    enrollmentId,
                    webSourceParams.getRegistrationUri(),
                    webSourceRegistrationRequest.getWebDestination(),
                    webSourceRegistrationRequest.getAppDestination(),
                    Uri.parse(ANDROID_APP_SCHEME_URI_PREFIX + sContext.getPackageName()),
                    null,
                    webSourceRegistrationRequest.getTopOriginUri(),
                    AsyncRegistration.RegistrationType.WEB_SOURCE,
                    sourceType,
                    System.currentTimeMillis(),
                    0,
                    System.currentTimeMillis(),
                    AsyncRegistration.RedirectType.NONE,
                    0,
                    arDebugPermission);
        }
        return null;
    }

    private static AsyncRegistration createAsyncRegistration(
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
            int redirectCount,
            boolean debugKeyAllowed) {
        return new AsyncRegistration.Builder()
                .setId(iD)
                .setEnrollmentId(enrollmentId)
                .setRegistrationUri(registrationUri)
                .setWebDestination(webDestination)
                .setOsDestination(osDestination)
                .setRegistrant(registrant)
                .setVerifiedDestination(verifiedDestination)
                .setTopOrigin(topOrigin)
                .setType(registrationType.ordinal())
                .setSourceType(
                        registrationType == AsyncRegistration.RegistrationType.APP_SOURCE
                                        || registrationType
                                                == AsyncRegistration.RegistrationType.WEB_SOURCE
                                ? sourceType
                                : null)
                .setRequestTime(mRequestTime)
                .setRetryCount(mRetryCount)
                .setLastProcessingTime(mLastProcessingTime)
                .setRedirectType(redirectType)
                .setRedirectCount(redirectCount)
                .setDebugKeyAllowed(debugKeyAllowed)
                .build();
    }

    private WebSourceRegistrationRequest buildWebSourceRegistrationRequest(
            List<WebSourceParams> sourceParamsList,
            String topOrigin,
            Uri appDestination,
            Uri webDestination) {
        WebSourceRegistrationRequest.Builder webSourceRegistrationRequestBuilder =
                new WebSourceRegistrationRequest.Builder(sourceParamsList, Uri.parse(topOrigin));
        if (appDestination != null) {
            webSourceRegistrationRequestBuilder.setAppDestination(appDestination);
        }
        if (webDestination != null) {
            webSourceRegistrationRequestBuilder.setWebDestination(webDestination);
        }
        return webSourceRegistrationRequestBuilder.build();
    }

    static Source.SourceType getSourceType(InputEvent inputEvent) {
        return inputEvent == null ? Source.SourceType.EVENT : Source.SourceType.NAVIGATION;
    }

    static Map<String, List<String>> getDefaultHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(
                "Attribution-Reporting-Register-Source",
                List.of(
                        "{\n"
                                + "\"destination\": \""
                                + DEFAULT_DESTINATION
                                + "\",\n"
                                + "\"source_event_id\": \""
                                + DEFAULT_EVENT_ID
                                + "\",\n"
                                + "\"priority\": \""
                                + DEFAULT_PRIORITY
                                + "\",\n"
                                + "\"expiry\": "
                                + DEFAULT_EXPIRY
                                + ""
                                + "}\n"));
        return headers;
    }

    private static void assertDefaultSourceRegistration(Source result) {
        assertEquals(ENROLLMENT_ID, result.getEnrollmentId());
        assertEquals(DEFAULT_DESTINATION, result.getAppDestinations().get(0).toString());
        assertEquals(DEFAULT_EVENT_ID, result.getEventId());
        assertEquals(DEFAULT_PRIORITY, result.getPriority());
        assertEquals(
                result.getEventTime() + TimeUnit.SECONDS.toMillis(DEFAULT_EXPIRY_ROUNDED),
                result.getExpiryTime());
    }

    private RegistrationRequest.Builder buildDefaultRegistrationRequestBuilder(
            String registrationUri) {
        return new RegistrationRequest.Builder(
                RegistrationRequest.REGISTER_SOURCE,
                Uri.parse(registrationUri),
                sContext.getAttributionSource().getPackageName(),
                SDK_PACKAGE_NAME);
    }

    public static InputEvent getInputEvent() {
        return obtain(
                0 /*long downTime*/,
                0 /*long eventTime*/,
                ACTION_BUTTON_PRESS,
                1 /*int pointerCount*/,
                new PointerProperties[] { new PointerProperties() },
                new PointerCoords[] { new PointerCoords() },
                0 /*int metaState*/,
                0 /*int buttonState*/,
                1.0f /*float xPrecision*/,
                1.0f /*float yPrecision*/,
                0 /*int deviceId*/,
                0 /*int edgeFlags*/,
                InputDevice.SOURCE_TOUCH_NAVIGATION,
                0 /*int flags*/);
    }
}
