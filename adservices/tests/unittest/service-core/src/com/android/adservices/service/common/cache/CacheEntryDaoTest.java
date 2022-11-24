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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

@SmallTest
public class CacheEntryDaoTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String URL = "https://google.com";
    private static final String BODY = "This is the Google home page";
    private static final long MAX_AGE_SECONDS = 1000;
    private CacheEntryDao mCacheEntryDao;
    private final DBCacheEntry mCacheEntry =
            DBCacheEntry.builder()
                    .setUrl(URL)
                    .setResponseBody(BODY)
                    .setCreationTimestamp(Instant.now())
                    .setMaxAgeSeconds(MAX_AGE_SECONDS)
                    .build();

    @Before
    public void setup() {
        mCacheEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();
    }

    @Test
    public void test_CacheIsEmptyByDefault() {
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());
    }

    @Test
    public void test_CacheEntryPut_Succeeds() throws ExecutionException, InterruptedException {
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());

        assertEquals(
                "One entry should have been inserted",
                1,
                mCacheEntryDao.persistCacheEntry(mCacheEntry));
        assertEquals(
                "Persistence layer should have 1 entry", 1, mCacheEntryDao.getDBEntriesCount());
    }

    @Test
    public void test_CacheEntryGet_Succeeds() {
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());
        mCacheEntryDao.persistCacheEntry(mCacheEntry);
        assertEquals(
                "Persistence layer should have 1 entry", 1, mCacheEntryDao.getDBEntriesCount());

        DBCacheEntry fetchedEntry = mCacheEntryDao.getCacheEntry(URL, Instant.now());
        assertEquals(
                "Both entries' body should have been the same",
                fetchedEntry.getResponseBody(),
                mCacheEntry.getResponseBody());
    }

    @Test
    public void test_CacheEntryGetStaleEntry_Failure() throws InterruptedException {
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());
        long smallEntryMaxAgeSeconds = 1;
        DBCacheEntry cacheEntry =
                DBCacheEntry.builder()
                        .setUrl(URL)
                        .setResponseBody(BODY)
                        .setCreationTimestamp(Instant.now())
                        .setMaxAgeSeconds(smallEntryMaxAgeSeconds)
                        .build();
        mCacheEntryDao.persistCacheEntry(cacheEntry);
        Thread.sleep(2 * 1000 * smallEntryMaxAgeSeconds);
        assertNull(
                "No stale cached entry should have returned",
                mCacheEntryDao.getCacheEntry(URL, Instant.now()));
    }

    @Test
    public void test_CacheEntryDeleteAll_Succeeds() {
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());
        mCacheEntryDao.persistCacheEntry(mCacheEntry);
        assertEquals(
                "Persistence layer should have 1 entry", 1, mCacheEntryDao.getDBEntriesCount());

        mCacheEntryDao.deleteAll();
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());
    }

    @Test
    public void test_CacheEntryDeleteStaleQueries_SmallMaxAge_Succeeds()
            throws InterruptedException {
        long smallEntryMaxAgeSeconds = 1;
        long largeDefaultMaxAgeSeconds = 100;
        DBCacheEntry cacheEntry =
                DBCacheEntry.builder()
                        .setUrl(URL)
                        .setResponseBody(BODY)
                        .setCreationTimestamp(Instant.now())
                        .setMaxAgeSeconds(smallEntryMaxAgeSeconds)
                        .build();
        mCacheEntryDao.persistCacheEntry(cacheEntry);
        assertEquals(
                "Persistence layer should have 1 entry", 1, mCacheEntryDao.getDBEntriesCount());

        Thread.sleep(2 * 1000 * smallEntryMaxAgeSeconds);
        mCacheEntryDao.deleteStaleRows(largeDefaultMaxAgeSeconds, Instant.now());
        assertEquals(
                "Persistence layer should have been empty", 0, mCacheEntryDao.getDBEntriesCount());
    }

    @Test
    public void test_CachePrunedToDesiredSizeAndFIFO_Success() throws InterruptedException {
        int fakeEntriesCount = 100;
        Instant fewMinutesAgo = Instant.now().minus(Duration.ofSeconds(fakeEntriesCount));

        // Put entries in increasing order of time
        for (int i = 0; i < fakeEntriesCount; i++) {
            DBCacheEntry entry =
                    DBCacheEntry.builder()
                            .setUrl(URL + i)
                            .setResponseBody(BODY)
                            .setCreationTimestamp(fewMinutesAgo.plus(Duration.ofSeconds(i)))
                            .setMaxAgeSeconds(MAX_AGE_SECONDS)
                            .build();
            mCacheEntryDao.persistCacheEntry(entry);
        }
        assertEquals(
                "Cache should have been populated with entries",
                fakeEntriesCount,
                mCacheEntryDao.getDBEntriesCount());

        // Prune to a desired size
        int maxCacheEntries = 10;
        mCacheEntryDao.prune(maxCacheEntries);
        // Some wait time for pruning to complete
        Thread.sleep(200);
        assertEquals(
                "Cache should have been pruned",
                maxCacheEntries,
                mCacheEntryDao.getDBEntriesCount());

        // Check that pruning is FIFO and older entries are removed first
        mCacheEntryDao.prune(1);
        // Some wait time for pruning to complete
        Thread.sleep(200);
        assertEquals(
                "After pruning cached size should have reduced",
                1,
                mCacheEntryDao.getDBEntriesCount());
        assertNotNull(
                "After pruning only latest entry should have remained",
                mCacheEntryDao.getCacheEntry(URL + (fakeEntriesCount - 1), Instant.now()));
    }
}
