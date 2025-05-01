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

package com.android.adservices.service.adid;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.proto.AdIdStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AdIdProtoDataStoreTest extends AdServicesUnitTestCase {
    private static final AdIdStorage AD_ID_STORAGE_EXAMPLE =
            AdIdStorage.newBuilder().setAdId("123").setLimitAdsTracking(true).build();

    private AdIdProtoDataStore mDataStore;

    @Before
    public void setup() {
        mDataStore =
                new AdIdProtoDataStore(AdIdStorage.class.getSimpleName() + getTestInvocationId());
    }

    @After
    public void tearDown() throws Exception {
        var unused = mDataStore.updateDataAsync(data -> AdIdStorage.getDefaultInstance()).get();
    }

    @Test
    public void testReadAndWriteProto() throws Exception {
        expect.that(mDataStore.updateDataAsync(proto -> AD_ID_STORAGE_EXAMPLE).get())
                .isEqualTo(AD_ID_STORAGE_EXAMPLE);
        expect.that(mDataStore.getDataAsync().get()).isEqualTo(AD_ID_STORAGE_EXAMPLE);

        // update some fields and validate again
        AdIdStorage updatedProto =
                AD_ID_STORAGE_EXAMPLE.toBuilder().setLimitAdsTracking(false).build();
        expect.that(
                        mDataStore
                                .updateDataAsync(
                                        proto ->
                                                proto.toBuilder()
                                                        .setLimitAdsTracking(false)
                                                        .build())
                                .get())
                .isEqualTo(updatedProto);
        expect.that(mDataStore.getDataAsync().get()).isEqualTo(updatedProto);
    }

    @Test
    public void testReadDefaultValue() throws Exception {
        expect.that(mDataStore.getDataAsync().get()).isEqualTo(AdIdStorage.getDefaultInstance());
    }
}
