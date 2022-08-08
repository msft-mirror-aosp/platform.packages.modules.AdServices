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
package android.adservices.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

/** Unit tests for {@link DeletionParam} */
@SmallTest
public final class DeletionParamTest {
    private static final String TAG = "DeletionRequestTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    private DeletionParam createExample() {
        return new DeletionParam.Builder()
                .setOriginUris(Collections.singletonList(Uri.parse("http://foo.com")))
                .setDomainUris(Collections.emptyList())
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                .setDeletionMode(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA)
                .setStart(Instant.ofEpochMilli(1642060000000L))
                .setEnd(Instant.ofEpochMilli(1642060538000L))
                .setPackageName(sContext.getAttributionSource().getPackageName())
                .build();
    }

    private DeletionParam createDefaultExample() {
        return new DeletionParam.Builder()
                .setOriginUris(Collections.emptyList())
                .setDomainUris(Collections.emptyList())
                .setStart(null)
                .setEnd(null)
                .setPackageName(sContext.getAttributionSource().getPackageName())
                .build();
    }

    void verifyExample(DeletionParam request) {
        assertEquals(1, request.getOriginUris().size());
        assertEquals("http://foo.com", request.getOriginUris().get(0).toString());
        assertTrue(request.getDomainUris().isEmpty());
        assertEquals(DeletionRequest.MATCH_BEHAVIOR_PRESERVE, request.getMatchBehavior());
        assertEquals(
                DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA, request.getDeletionMode());
        assertEquals(1642060000000L, request.getStart().toEpochMilli());
        assertEquals(1642060538000L, request.getEnd().toEpochMilli());
        assertNotNull(request.getPackageName());
    }

    void verifyDefaultExample(DeletionParam request) {
        assertTrue(request.getOriginUris().isEmpty());
        assertTrue(request.getDomainUris().isEmpty());
        assertEquals(DeletionRequest.MATCH_BEHAVIOR_DELETE, request.getMatchBehavior());
        assertEquals(DeletionRequest.DELETION_MODE_ALL, request.getDeletionMode());
        assertNull(request.getStart());
        assertNull(request.getEnd());
        assertNotNull(request.getPackageName());
    }

    @Test
    public void testMissingParams() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new DeletionParam.Builder().build();
                });
    }

    @Test
    public void testDefaults() {
        verifyDefaultExample(createDefaultExample());
    }

    @Test
    public void testCreation() {
        verifyExample(createExample());
    }

    @Test
    public void testParcelingDelete() {
        Parcel p = Parcel.obtain();
        createExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExample(DeletionParam.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testParcelingDeleteDefaults() {
        Parcel p = Parcel.obtain();
        createDefaultExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyDefaultExample(DeletionParam.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExample().describeContents());
    }
}
