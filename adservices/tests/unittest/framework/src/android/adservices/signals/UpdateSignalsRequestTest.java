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

package android.adservices.signals;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@RequiresSdkLevelAtLeastT
public final class UpdateSignalsRequestTest extends AdServicesUnitTestCase {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final Uri OTHER_URI = Uri.parse("https://example.com/lesscoolsignals");

    @Test
    public void testBuild() {
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(URI).build();
        assertThat(request.getUpdateUri()).isEqualTo(URI);
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(
                NullPointerException.class, () -> new UpdateSignalsRequest.Builder(null).build());
    }

    @Test
    public void testSetUpdateUri() {
        UpdateSignalsRequest request =
                new UpdateSignalsRequest.Builder(URI).setUpdateUri(URI).build();
        assertThat(request.getUpdateUri()).isEqualTo(URI);
    }

    @Test
    public void testEqualsEqual() {
        EqualsTester et = new EqualsTester(expect);
        UpdateSignalsRequest identical1 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest identical2 = new UpdateSignalsRequest.Builder(URI).build();
        et.expectObjectsAreEqual(identical1, identical2);
    }

    @Test
    public void testEqualsNotEqualSameClass() {
        EqualsTester et = new EqualsTester(expect);
        UpdateSignalsRequest different1 = new UpdateSignalsRequest.Builder(URI).build();
        UpdateSignalsRequest different2 = new UpdateSignalsRequest.Builder(OTHER_URI).build();
        et.expectObjectsAreNotEqual(different1, different2);
    }

    @Test
    public void testEqualsNotEqualDifferentClass() {
        EqualsTester et = new EqualsTester(expect);
        UpdateSignalsRequest input1 = new UpdateSignalsRequest.Builder(URI).build();
        et.expectObjectsAreNotEqual(input1, new Object());
    }

    @Test
    public void testToString() {
        UpdateSignalsRequest input = new UpdateSignalsRequest.Builder(URI).build();
        assertThat(input.toString()).isEqualTo("UpdateSignalsRequest{" + "updateUri=" + URI + '}');
    }
}
