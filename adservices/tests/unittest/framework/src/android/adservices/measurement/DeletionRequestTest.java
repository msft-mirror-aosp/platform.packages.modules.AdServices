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

import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

/** Unit test for {@link android.adservices.measurement.DeletionRequest} */
public final class DeletionRequestTest extends AdServicesUnitTestCase {
    private static final Uri ORIGIN_URI = Uri.parse("https://a.foo.com");
    private static final Uri DOMAIN_URI = Uri.parse("https://foo.com");
    private static final Instant START = Instant.ofEpochSecond(0);
    private static final Instant END = Instant.now();

    @Test
    public void testNonNullParams() {
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setDeletionMode(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(START)
                        .setEnd(END)
                        .build();

        expect.that(request.getStart()).isEqualTo(START);
        expect.that(request.getEnd()).isEqualTo(END);
        expect.that(request.getOriginUris()).containsExactly(ORIGIN_URI);
        expect.that(request.getDomainUris()).containsExactly(DOMAIN_URI);
        expect.that(request.getDeletionMode())
                .isEqualTo(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA);
        expect.that(request.getMatchBehavior()).isEqualTo(DeletionRequest.MATCH_BEHAVIOR_PRESERVE);
    }

    @Test
    public void testNullParams() {
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setDomainUris(null)
                        .setOriginUris(null)
                        .setStart(START)
                        .setEnd(END)
                        .build();
        expect.that(request.getOriginUris()).isEmpty();
        expect.that(request.getDomainUris()).isEmpty();
        expect.that(request.getDeletionMode()).isEqualTo(DeletionRequest.DELETION_MODE_ALL);
        expect.that(request.getMatchBehavior()).isEqualTo(DeletionRequest.MATCH_BEHAVIOR_DELETE);
    }

    @Test
    public void testDefaultParams() {
        DeletionRequest request = new DeletionRequest.Builder().build();
        expect.that(request.getStart()).isEqualTo(Instant.MIN);
        expect.that(request.getEnd()).isEqualTo(Instant.MAX);
        expect.that(request.getOriginUris()).isEmpty();
        expect.that(request.getDomainUris()).isEmpty();
        expect.that(request.getDeletionMode()).isEqualTo(DeletionRequest.DELETION_MODE_ALL);
        expect.that(request.getMatchBehavior()).isEqualTo(DeletionRequest.MATCH_BEHAVIOR_DELETE);
    }

    @Test
    public void testNullStartThrowsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DeletionRequest.Builder().setStart(null).setEnd(END).build());
    }

    @Test
    public void testNullEndThrowsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DeletionRequest.Builder().setStart(START).setEnd(null).build());
    }

    @Test
    public void testMinAndMaxInstants() {
        DeletionRequest request =
                new DeletionRequest.Builder().setStart(Instant.MIN).setEnd(Instant.MAX).build();
        expect.that(request.getStart()).isEqualTo(Instant.MIN);
        expect.that(request.getEnd()).isEqualTo(Instant.MAX);
    }
}
