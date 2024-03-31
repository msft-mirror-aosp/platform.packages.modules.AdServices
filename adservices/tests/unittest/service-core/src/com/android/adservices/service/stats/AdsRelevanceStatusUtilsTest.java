/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.getDownloadTimeInBucketSize;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AdsRelevanceStatusUtilsTest {
    @Test
    public void testGetDownloadTimeInBucketSize() {
        // The bucket Size of -1 download time in millisecond should be SIZE_UNSET
        assertEquals(SIZE_UNSET, getDownloadTimeInBucketSize(-1));
        // The bucket Size of -100 download time in millisecond should be SIZE_UNSET
        assertEquals(SIZE_UNSET, getDownloadTimeInBucketSize(-100));
        // The bucket Size of 25 download time in millisecond should be SIZE_VERY_SMALL
        assertEquals(SIZE_VERY_SMALL, getDownloadTimeInBucketSize(25));
        // The bucket Size of 50 download time in millisecond should be SIZE_VERY_SMALL
        assertEquals(SIZE_VERY_SMALL, getDownloadTimeInBucketSize(50));
        // The bucket Size of 100 download time in millisecond should be SIZE_SMALL
        assertEquals(SIZE_SMALL, getDownloadTimeInBucketSize(100));
        // The bucket Size of 200 download time in millisecond should be SIZE_SMALL
        assertEquals(SIZE_SMALL, getDownloadTimeInBucketSize(200));
        // The bucket Size of 500 download time in millisecond should be SIZE_MEDIUM
        assertEquals(SIZE_MEDIUM, getDownloadTimeInBucketSize(500));
        // The bucket Size of 1000 download time in millisecond should be SIZE_MEDIUM
        assertEquals(SIZE_MEDIUM, getDownloadTimeInBucketSize(1000));
        // The bucket Size of 1500 download time in millisecond should be SIZE_UNSET
        assertEquals(SIZE_LARGE, getDownloadTimeInBucketSize(1500));
        // The bucket Size of 2000 download time in millisecond should be SIZE_UNSET
        assertEquals(SIZE_LARGE, getDownloadTimeInBucketSize(2000));
        // The bucket Size of 10000 download time in millisecond should be SIZE_UNSET
        assertEquals(SIZE_VERY_LARGE, getDownloadTimeInBucketSize(10000));
    }
}
