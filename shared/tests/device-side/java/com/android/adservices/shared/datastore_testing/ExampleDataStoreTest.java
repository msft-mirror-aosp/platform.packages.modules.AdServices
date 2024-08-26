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

package com.android.adservices.shared.datastore_testing;

import android.content.Context;
import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.datastore_testing.proto.ExampleDatastoreProto;
import com.android.adservices.shared.datastore_testing.proto.NestedProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// TODO(b/335935200): RavenwoodBaseContext.getApplicationContext() not supported
@DisabledOnRavenwood(blockedBy = Context.class)
public final class ExampleDataStoreTest extends SharedUnitTestCase {
    private static final ExampleDatastoreProto EXAMPLE_DATASTORE_PROTO =
            ExampleDatastoreProto.newBuilder()
                    .setIntField(1)
                    .setStringField("2")
                    .setBoolField(true)
                    .setDoubleField(2.1d)
                    .setFloatField(3.1f)
                    .setNestedProtoField(NestedProto.newBuilder().setAnotherIntField(5).build())
                    .build();

    private ExampleDataStore mDataStore;

    @Before
    public void setup() {
        mDataStore =
                new ExampleDataStore(
                        ExampleDatastoreProto.class.getSimpleName() + getTestInvocationId());
    }

    @After
    public void tearDown() throws Exception {
        var unused =
                mDataStore
                        .updateDataAsync(data -> ExampleDatastoreProto.getDefaultInstance())
                        .get();
    }

    @Test
    public void testReadAndWriteProto() throws Exception {
        expect.that(mDataStore.updateDataAsync(proto -> EXAMPLE_DATASTORE_PROTO).get())
                .isEqualTo(EXAMPLE_DATASTORE_PROTO);
        expect.that(mDataStore.getDataAsync().get()).isEqualTo(EXAMPLE_DATASTORE_PROTO);

        // update some fields and validate again
        ExampleDatastoreProto updatedProto =
                EXAMPLE_DATASTORE_PROTO.toBuilder()
                        .setNestedProtoField(
                                NestedProto.newBuilder().setAnotherIntField(52).build())
                        .build();
        expect.that(
                        mDataStore
                                .updateDataAsync(
                                        proto ->
                                                proto.toBuilder()
                                                        .setNestedProtoField(
                                                                NestedProto.newBuilder()
                                                                        .setAnotherIntField(52)
                                                                        .build())
                                                        .build())
                                .get())
                .isEqualTo(updatedProto);
        expect.that(mDataStore.getDataAsync().get()).isEqualTo(updatedProto);
    }

    @Test
    public void testReadDefaultValue() throws Exception {
        expect.that(mDataStore.getDataAsync().get())
                .isEqualTo(ExampleDatastoreProto.getDefaultInstance());
    }
}
