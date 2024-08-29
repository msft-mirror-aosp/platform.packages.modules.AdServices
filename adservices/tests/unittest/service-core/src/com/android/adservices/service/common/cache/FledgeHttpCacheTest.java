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

package com.android.adservices.service.common.cache;

import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_MAX_AGE;
import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_MAX_AGE_SEPARATOR;
import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_NO_CACHE;
import static com.android.adservices.service.common.cache.FledgeHttpCache.PROPERTY_NO_STORE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.room.Room;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HttpHeaders;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public final class FledgeHttpCacheTest extends AdServicesMockitoTestCase {
    private static final long MAX_AGE_SECONDS = 2 * 24 * 60 * 60;
    private static final long MAX_ENTRIES = 20;
    private URL mUrl;
    private String mBody;
    private DBCacheEntry mCacheEntry;
    private FledgeHttpCache mCache;
    private ExecutorService mExecutorService;
    private Map<String, List<String>> mCachingPropertiesMap;
    private ImmutableMap<String, List<String>> mResponseHeadersMap;

    @Mock private CacheEntryDao mCacheEntryDaoMock;
    @Mock private HttpCache.CacheObserver mObserver;
    @Captor private ArgumentCaptor<DBCacheEntry> mCacheEntryArgumentCaptor;
    @Captor private ArgumentCaptor<Long> mCacheMaxAgeCaptor;
    @Captor private ArgumentCaptor<Long> mCacheMaxEntriesCaptor;

    @Before
    public void setup() throws MalformedURLException {
        mUrl = new URL("https://google.com");
        mBody = "This is the Google home page";
        mCachingPropertiesMap = new HashMap<>();
        mResponseHeadersMap =
                ImmutableMap.of(
                        "header_1",
                        ImmutableList.of("h1_value1", "h1_value2"),
                        "header_2",
                        ImmutableList.of("h2_value1", "h2_value2"));
        mCacheEntry =
                DBCacheEntry.builder()
                        .setUrl(mUrl.toString())
                        .setResponseBody(mBody)
                        .setResponseHeaders(mResponseHeadersMap)
                        .setCreationTimestamp(Instant.now())
                        .setMaxAgeSeconds(1000)
                        .build();
        mExecutorService = AdServicesExecutors.getBackgroundExecutor();

        mCache =
                new FledgeHttpCache(
                        mCacheEntryDaoMock, mExecutorService, MAX_AGE_SECONDS, MAX_ENTRIES);
        mCache.addObserver(mObserver);
    }

    @Test
    public void test_CacheGetEmpty_ReturnsNull() {
        assertThat(mCache.get(mUrl)).isNull();
    }

    @Test
    public void test_CachePutEntry_Succeeds() {
        mCache.put(mUrl, mBody, mCachingPropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        expect.withMessage("Cached key should have been same")
                .that(mCacheEntryArgumentCaptor.getValue().getUrl())
                .isEqualTo(mUrl.toString());
        expect.withMessage("Cached body should have been same")
                .that(mCacheEntryArgumentCaptor.getValue().getResponseBody())
                .isEqualTo(mBody);
        expect.withMessage("Cached response headers should have been the same")
                .that(mCacheEntryArgumentCaptor.getValue().getResponseHeaders())
                .isEqualTo(mResponseHeadersMap);
        verify(mObserver).update(HttpCache.CacheEventType.PUT);
    }

    @Test
    public void test_CachePutEntryNoCache_SkipsCache() {
        List<String> skipCacheProperties = ImmutableList.of(PROPERTY_NO_CACHE);
        Map<String, List<String>> skipCacheMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, skipCacheProperties);
        mCache.put(mUrl, mBody, skipCacheMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock, times(0)).persistCacheEntry(any(DBCacheEntry.class));
        verify(mObserver, never()).update(HttpCache.CacheEventType.PUT);
    }

    @Test
    public void test_CachePutEntryNoStoreCache_SkipsCache() {
        List<String> skipCacheProperties = ImmutableList.of(PROPERTY_NO_STORE);
        Map<String, List<String>> skipCacheMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, skipCacheProperties);
        mCache.put(mUrl, mBody, skipCacheMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock, times(0)).persistCacheEntry(any(DBCacheEntry.class));
        verify(mObserver, never()).update(HttpCache.CacheEventType.PUT);
    }

    @Test
    public void test_CacheGetEntry_Succeeds() {
        doReturn(mCacheEntry)
                .when(mCacheEntryDaoMock)
                .getCacheEntry(eq(mUrl.toString()), any(Instant.class));
        DBCacheEntry cacheFetchedResponse = mCache.get(mUrl);
        assertWithMessage("Response body should have been the same")
                .that(cacheFetchedResponse.getResponseBody())
                .isEqualTo(mBody);
        verify(mObserver).update(HttpCache.CacheEventType.GET);
    }

    @Test
    public void test_CacheGetTotalEntries_Succeeds() {
        doReturn(123L).when(mCacheEntryDaoMock).getDBEntriesCount();
        assertWithMessage("No of entries in cache mismatch")
                .that(mCache.getCachedEntriesCount())
                .isEqualTo(123L);
    }

    @Test
    public void test_CacheCleanUp_Succeeds() {
        mCache.cleanUp();
        verify(mCacheEntryDaoMock)
                .deleteStaleRows(mCacheMaxAgeCaptor.capture(), any(Instant.class));
        long maxAgeValue = mCacheMaxAgeCaptor.getValue();
        assertWithMessage("Default max age for cache not consistent")
                .that(maxAgeValue)
                .isEqualTo(MAX_AGE_SECONDS);

        verify(mCacheEntryDaoMock).prune(mCacheMaxEntriesCaptor.capture());
        long maxEntriesValue = mCacheMaxEntriesCaptor.getValue();
        assertWithMessage("Default max entries for cache not consistent")
                .that(maxEntriesValue)
                .isEqualTo(MAX_ENTRIES);
        verify(mObserver).update(HttpCache.CacheEventType.CLEANUP);
    }

    @Test
    public void test_CacheDelete_DeletesAll() {
        mCache.delete();
        verify(mCacheEntryDaoMock).deleteAll();
        verify(mObserver).update(HttpCache.CacheEventType.DELETE);
    }

    @Test
    public void test_GetCacheRequestMaxAge_Success() {
        long expectedAgeSeconds = 60;
        ImmutableList<String> cacheProperties =
                ImmutableList.of(
                        PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + expectedAgeSeconds);
        assertThat(mCache.getRequestMaxAgeSeconds(cacheProperties)).isEqualTo(expectedAgeSeconds);
    }

    @Test
    public void test_MaxAgeUpperBounded_GlobalMaxAge() {
        long reallyLongMaxAge = MAX_AGE_SECONDS * 5;
        List<String> maxCacheAgeProperties =
                ImmutableList.of(PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + reallyLongMaxAge);
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        mCache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertWithMessage("The max age should not have been more than default max age")
                .that(mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds())
                .isEqualTo(MAX_AGE_SECONDS);
    }

    @Test
    public void test_MaxAgeLowerBounded_RequestMaxAge() {
        long reallySmallMaxAge = 5;
        List<String> maxCacheAgeProperties =
                ImmutableList.of(PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + reallySmallMaxAge);
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        mCache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertWithMessage("The max age should have been set to value in the request headers")
                .that(mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds())
                .isEqualTo(reallySmallMaxAge);
    }

    @Test
    public void test_CacheE2ESetDefaultMaxAge_GarbledMaxAge() {
        List<String> maxCacheAgeProperties = ImmutableList.of("garbled-max-age-param=2000ABC");
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        mCache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertWithMessage("Cached entry max age does not match default")
                .that(mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds())
                .isEqualTo(MAX_AGE_SECONDS);
    }

    @Test
    public void test_CacheE2ESetDefaultMaxAge_MissingMaxAge() {
        mCache.put(mUrl, mBody, Collections.emptyMap(), mResponseHeadersMap);
        verify(mCacheEntryDaoMock).persistCacheEntry(mCacheEntryArgumentCaptor.capture());
        assertWithMessage("Cache entry max age does not match default")
                .that(mCacheEntryArgumentCaptor.getValue().getMaxAgeSeconds())
                .isEqualTo(MAX_AGE_SECONDS);
    }

    /** This test uses real Dao to check the actual cache contracts put and get */
    @Test
    public void test_CacheE2EPutAndGet_Success() {
        CacheEntryDao realDao =
                Room.inMemoryDatabaseBuilder(mContext, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();
        FledgeHttpCache cache =
                new FledgeHttpCache(realDao, mExecutorService, MAX_AGE_SECONDS, MAX_ENTRIES);
        cache.put(mUrl, mBody, mCachingPropertiesMap, mResponseHeadersMap);
        expect.withMessage("Cache should have persisted one entry")
                .that(cache.getCachedEntriesCount())
                .isEqualTo(1);
        expect.withMessage("Cached response does not match original")
                .that(cache.get(mUrl).getResponseBody())
                .isEqualTo(mBody);
    }

    @Test
    public void test_CacheE2EHonorsMaxAge_Success() {
        CacheEntryDao realDao =
                Room.inMemoryDatabaseBuilder(mContext, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();
        FledgeHttpCache cache =
                new FledgeHttpCache(realDao, mExecutorService, MAX_AGE_SECONDS, MAX_ENTRIES);
        long reallySmallMaxAge = 0;
        List<String> maxCacheAgeProperties =
                ImmutableList.of(PROPERTY_MAX_AGE + PROPERTY_MAX_AGE_SEPARATOR + reallySmallMaxAge);
        Map<String, List<String>> cachePropertiesMap =
                ImmutableMap.of(HttpHeaders.CACHE_CONTROL, maxCacheAgeProperties);
        cache.put(mUrl, mBody, cachePropertiesMap, mResponseHeadersMap);
        expect.withMessage("Cache should have persisted one entry")
                .that(cache.getCachedEntriesCount())
                .isEqualTo(1);
        expect.withMessage("Entries past their max-age should not be fetched")
                .that(cache.get(mUrl))
                .isNull();
    }
}
