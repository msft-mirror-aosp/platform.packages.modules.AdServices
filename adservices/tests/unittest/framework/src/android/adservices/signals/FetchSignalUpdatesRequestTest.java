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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import org.junit.Test;

public class FetchSignalUpdatesRequestTest {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final Uri OTHER_URI = Uri.parse("https://example.com/lesscoolsignals");

    @Test
    public void testBuild() {
        FetchSignalUpdatesRequest request = new FetchSignalUpdatesRequest.Builder(URI).build();
        assertEquals(URI, request.getFetchUri());
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FetchSignalUpdatesRequest.Builder(null).build());
    }

    @Test
    public void testEqualsEqual() {
        FetchSignalUpdatesRequest identical1 = new FetchSignalUpdatesRequest.Builder(URI).build();
        FetchSignalUpdatesRequest identical2 = new FetchSignalUpdatesRequest.Builder(URI).build();
        assertEquals(identical1, identical2);
    }

    @Test
    public void testEqualsNotEqualSameClass() {
        FetchSignalUpdatesRequest different1 = new FetchSignalUpdatesRequest.Builder(URI).build();
        FetchSignalUpdatesRequest different2 =
                new FetchSignalUpdatesRequest.Builder(OTHER_URI).build();
        assertNotEquals(different1, different2);
    }

    @Test
    public void testEqualsNotEqualDifferentClass() {
        FetchSignalUpdatesRequest input1 = new FetchSignalUpdatesRequest.Builder(URI).build();
        assertNotEquals(input1, new Object());
    }

    @Test
    public void testHash() {
        FetchSignalUpdatesRequest identical1 = new FetchSignalUpdatesRequest.Builder(URI).build();
        FetchSignalUpdatesRequest identical2 = new FetchSignalUpdatesRequest.Builder(URI).build();
        assertEquals(identical1.hashCode(), identical2.hashCode());
    }

    @Test
    public void testToString() {
        FetchSignalUpdatesRequest input = new FetchSignalUpdatesRequest.Builder(URI).build();
        assertEquals("FetchSignalUpdatesRequest{" + "fetchUri=" + URI + '}', input.toString());
    }
}
