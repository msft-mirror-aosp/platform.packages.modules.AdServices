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

package com.android.adservices.data.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public final class AtomicFileDatastoreTest extends AdServicesMockitoTestCase {
    private static final String FILENAME = "AtomicFileDatastoreTest.xml";
    private static final int DATASTORE_VERSION = 1;

    private AtomicFileDatastore mDatastore;

    @Before
    public void initializeDatastore() throws IOException {
        mDatastore = new AtomicFileDatastore(mContext, FILENAME, DATASTORE_VERSION);
        mDatastore.initialize();
    }

    @After
    public void cleanupDatastore() {
        mDatastore.tearDownForTesting();
    }

    @Test
    public void testGetVersionKey() {
        assertWithMessage("getVersionKey()")
                .that(mDatastore.getVersionKey())
                .isEqualTo(AtomicFileDatastore.VERSION_KEY);
    }

    @Test
    public void testConstructor_emptyOrNullArgs() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AtomicFileDatastore(
                                /* adServicesContext= */ null, FILENAME, DATASTORE_VERSION));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AtomicFileDatastore(mContext, /* filename= */ null, DATASTORE_VERSION));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AtomicFileDatastore(mContext, /* filename= */ "", DATASTORE_VERSION));
    }
}
