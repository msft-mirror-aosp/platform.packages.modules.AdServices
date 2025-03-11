/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.signals.UpdateSignalsInput;
import android.adservices.signals.UpdateSignalsRequest;
import android.net.Uri;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/**
 * Unit tests for {@link UpdateSignalsInput}
 *
 * <p>If this class is un-ignored {@link android.adservices.signals.UpdateSignalsInputTest} should
 * be deleted.
 */
public final class UpdateSignalsRequestTest extends CtsAdServicesDeviceTestCase {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final Uri OTHER_URI = Uri.parse("https://example.com/lesscoolsignals");

    @Test
    public void testBuild() {
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(URI).build();
        expect.that(request.getUpdateUri()).isEqualTo(URI);
    }

    @Test
    public void testSetUpdateUri() {
        UpdateSignalsRequest request =
                new UpdateSignalsRequest.Builder(URI).setUpdateUri(URI).build();
        expect.that(request.getUpdateUri()).isEqualTo(URI);
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(NullPointerException.class, () -> new UpdateSignalsRequest.Builder(null));
    }

    @Test
    public void testEqualsEqual() {
        UpdateSignalsRequest identical1 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest identical2 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest different2 = new UpdateSignalsRequest.Builder(OTHER_URI).build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(identical1, identical2);
        et.expectObjectsAreNotEqual(identical1, different2);
        et.expectObjectsAreNotEqual(identical1, new Object());
    }

    @Test
    public void testToString() {
        UpdateSignalsRequest input = new UpdateSignalsRequest.Builder(URI).build();
        assertThat(input.toString()).isEqualTo("UpdateSignalsRequest{updateUri=" + URI + '}');
    }
}
