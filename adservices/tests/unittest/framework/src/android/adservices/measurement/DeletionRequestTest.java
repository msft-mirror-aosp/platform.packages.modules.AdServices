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

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.time.Instant;

/** Unit test for {@link android.adservices.measurement.DeletionRequest} */
@SmallTest
public class DeletionRequestTest {
    private static final String ORIGIN_URI = "http://foo.com";
    private static final Instant START = Instant.ofEpochSecond(0);
    private static final Instant END = Instant.now();

    @Test
    public void testNonNullParams() {
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUri(Uri.parse(ORIGIN_URI))
                        .setStart(START)
                        .setEnd(END)
                        .build();

        assertThat(request.getOriginUri()).isEqualTo(Uri.parse(ORIGIN_URI));
        assertThat(request.getStart()).isEqualTo(START);
        assertThat(request.getEnd()).isEqualTo(END);
    }

    @Test
    public void testNullParams() {
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUri(null)
                        .setStart(null)
                        .setEnd(null)
                        .build();

        assertThat(request.getOriginUri()).isEqualTo(null);
        assertThat(request.getStart()).isEqualTo(null);
        assertThat(request.getEnd()).isEqualTo(null);
    }

    @Test
    public void testDefaultParams() {
        DeletionRequest request = new DeletionRequest.Builder().build();

        assertThat(request.getOriginUri()).isEqualTo(null);
        assertThat(request.getStart()).isEqualTo(null);
        assertThat(request.getEnd()).isEqualTo(null);
    }
}
