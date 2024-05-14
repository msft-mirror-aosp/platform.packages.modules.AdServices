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

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

/** Unit tests for {@link DeletionParam} */
public final class DeletionParamTest extends AdServicesUnitTestCase {
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";

    private DeletionParam createExample() {
        return new DeletionParam.Builder(
                        Collections.singletonList(Uri.parse("http://foo.com")),
                        Collections.emptyList(),
                        Instant.ofEpochMilli(1642060000000L),
                        Instant.ofEpochMilli(1642060538000L),
                        sContext.getPackageName(),
                        "sdk.package.name")
                .setDeletionMode(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA)
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                .build();
    }

    private DeletionParam createDefaultExample() {
        return new DeletionParam.Builder(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Instant.MIN,
                        Instant.MAX,
                        sContext.getPackageName(),
                        /* sdkPackageName = */ "")
                .build();
    }

    void verifyExample(DeletionParam request) {
        expect.that(request.getOriginUris()).hasSize(1);
        expect.that(request.getOriginUris().get(0).toString()).isEqualTo("http://foo.com");
        expect.that(request.getDomainUris()).isEmpty();
        expect.that(request.getMatchBehavior()).isEqualTo(DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
        expect.that(request.getDeletionMode())
                .isEqualTo(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA);
        expect.that(request.getStart().toEpochMilli()).isEqualTo(1642060000000L);
        expect.that(request.getEnd().toEpochMilli()).isEqualTo(1642060538000L);
        expect.that(request.getAppPackageName()).isNotNull();
    }

    void verifyDefaultExample(DeletionParam request) {
        expect.that(request.getOriginUris()).isEmpty();
        expect.that(request.getDomainUris()).isEmpty();
        expect.that(request.getMatchBehavior()).isEqualTo(DeletionRequest.MATCH_BEHAVIOR_DELETE);
        expect.that(request.getDeletionMode()).isEqualTo(DeletionRequest.DELETION_MODE_ALL);
        expect.that(request.getStart()).isEqualTo(Instant.MIN);
        expect.that(request.getEnd()).isEqualTo(Instant.MAX);
        expect.that(request.getAppPackageName()).isNotNull();
    }

    @Test
    public void testMissingOrigin_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        /* originUris = */ null,
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingDomainUris_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        /* domainUris = */ null,
                                        Instant.MIN,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingStart_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        /* start = */ null,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingEnd_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        /* end = */ null,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingAppPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        /* appPackageName = */ null,
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingSdkPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        /* sdkPackageName = */ null)
                                .build());
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
        expect.that(createExample().describeContents()).isEqualTo(0);
    }
}
