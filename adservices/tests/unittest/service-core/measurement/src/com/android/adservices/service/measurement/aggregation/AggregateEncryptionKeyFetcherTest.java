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
package com.android.adservices.service.measurement.aggregation;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_INVALID_PARAMETER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;

import org.json.JSONException;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;

/** Unit tests for {@link AggregateEncryptionKeyFetcher} */
@SmallTest
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
public final class AggregateEncryptionKeyFetcherTest extends AdServicesExtendedMockitoTestCase {
    @Spy AggregateEncryptionKeyFetcher mFetcher = new AggregateEncryptionKeyFetcher(mContext);

    @Mock HttpsURLConnection mUrlConnection;

    @Test
    public void testBasicAggregateEncryptionKeyRequest() throws Exception {
        AggregateEncryptionKeyTestUtil.prepareMockAggregateEncryptionKeyFetcher(
                mFetcher, mUrlConnection, AggregateEncryptionKeyTestUtil.getDefaultResponseBody());
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();

        assertThat(result.size()).isEqualTo(2);

        assertThat(result.get(0).getKeyId())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID);
        assertThat(result.get(0).getPublicKey())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY);
        assertThat(result.get(0).getExpiry())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY);

        assertThat(result.get(1).getKeyId())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID);
        assertThat(result.get(1).getPublicKey())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY);
        assertThat(result.get(1).getExpiry())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY);

        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testBadSourceUrl() throws Exception {
        Uri badTarget = WebUtil.validUri("bad-schema://foo.test");
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        badTarget,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = MalformedURLException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_INVALID_PARAMETER)
    public void testMalformedUrl() throws Exception {
        Uri invalidPort = WebUtil.validUri("https://foo.test:-1");
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        invalidPort,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = IOException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR)
    public void testBadConnection() throws Exception {
        doThrow(new IOException("Bad internet things")).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = IOException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_IO_ERROR)
    public void testServerTimeout() throws Exception {
        doReturn(mUrlConnection)
                .when(mFetcher)
                .openUrl(new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of());
        doThrow(new IOException("timeout")).when(mUrlConnection).getResponseCode();

        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = JSONException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_PUBLIC_KEY_FETCHER_PARSING_ERROR)
    public void testInvalidResponseBodyJson() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                ("{" + AggregateEncryptionKeyTestUtil.getDefaultResponseBody()).getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE)));
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    public void testMissingCacheControlHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(new HashMap());
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    public void testMissingAgeHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        long expectedExpiry = AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY
                + Long.parseLong(AggregateEncryptionKeyTestUtil.DEFAULT_CACHED_AGE) * 1000;
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE)));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();

        assertThat(result.size()).isEqualTo(2);

        assertThat(result.get(0).getKeyId())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID);
        assertThat(result.get(0).getPublicKey())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY);
        assertThat(result.get(0).getExpiry()).isEqualTo(expectedExpiry);

        assertThat(result.get(1).getKeyId())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID);
        assertThat(result.get(1).getPublicKey())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY);
        assertThat(result.get(0).getExpiry()).isEqualTo(expectedExpiry);

        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testBrokenAgeHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        long expectedExpiry = AggregateEncryptionKeyTestUtil.DEFAULT_EXPIRY
                + Long.parseLong(AggregateEncryptionKeyTestUtil.DEFAULT_CACHED_AGE) * 1000;
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE),
                "age", List.of("not an int")));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();

        assertThat(result.size()).isEqualTo(2);

        assertThat(result.get(0).getKeyId())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.KEY_ID);
        assertThat(result.get(0).getPublicKey())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_1.PUBLIC_KEY);
        assertThat(result.get(0).getExpiry()).isEqualTo(expectedExpiry);

        assertThat(result.get(1).getKeyId())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.KEY_ID);
        assertThat(result.get(1).getPublicKey())
                .isEqualTo(AggregateEncryptionKeyTestUtil.DEFAULT_KEY_2.PUBLIC_KEY);
        assertThat(result.get(0).getExpiry()).isEqualTo(expectedExpiry);

        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testCachedAgeGreaterThanMaxAge() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                AggregateEncryptionKeyTestUtil.getDefaultResponseBody().getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(
                new URL(AggregateEncryptionKeyTestUtil.DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(AggregateEncryptionKeyTestUtil.DEFAULT_MAX_AGE),
                "age", List.of("604801")));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        AggregateEncryptionKeyTestUtil.DEFAULT_COORDINATOR_ORIGIN,
                        AggregateEncryptionKeyTestUtil.DEFAULT_TARGET,
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
    }

    @Test
    public void testNotOverHttps() throws Exception {
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(
                        WebUtil.validUri("http://foo.test"),
                        WebUtil.validUri("http://foo.test"),
                        AggregateEncryptionKeyTestUtil.DEFAULT_EVENT_TIME);
        assertThat(resultOptional.isPresent()).isFalse();
        verify(mFetcher, never()).openUrl(any());
    }
}
