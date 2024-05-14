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

package android.adservices.measurement;

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class SourceRegistrationRequestTest extends AdServicesUnitTestCase {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://bar.test");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.test");
    private static final Uri INVALID_REGISTRATION_URI = Uri.parse("http://bar.test");
    private static final List<Uri> SOURCE_REGISTRATIONS =
            Arrays.asList(REGISTRATION_URI_1, REGISTRATION_URI_2);
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private static final SourceRegistrationRequest SOURCE_REGISTRATION_REQUEST =
            createExampleRegistrationRequest();

    @Test
    public void testDefaults() throws Exception {
        SourceRegistrationRequest request =
                new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS).build();

        expect.that(request.getRegistrationUris()).isEqualTo(SOURCE_REGISTRATIONS);
        expect.that(request.getInputEvent()).isNull();
    }

    @Test
    public void build_withAllFieldsPopulated_successfullyRetrieved() {
        SourceRegistrationRequest request = createExampleRegistrationRequest();
        expect.that(request.getRegistrationUris()).isEqualTo(SOURCE_REGISTRATIONS);
        expect.that(request.getInputEvent()).isEqualTo(INPUT_KEY_EVENT);
    }

    @Test
    public void writeToParcel_withInputEvent_success() {
        Parcel p = Parcel.obtain();
        SOURCE_REGISTRATION_REQUEST.writeToParcel(p, 0);
        p.setDataPosition(0);
        SourceRegistrationRequest fromParcel =
                SourceRegistrationRequest.CREATOR.createFromParcel(p);
        expect.that(fromParcel.getRegistrationUris()).isEqualTo(SOURCE_REGISTRATIONS);
        expect.that(((KeyEvent) fromParcel.getInputEvent()).getAction())
                .isEqualTo(INPUT_KEY_EVENT.getAction());
        expect.that(((KeyEvent) fromParcel.getInputEvent()).getKeyCode())
                .isEqualTo(INPUT_KEY_EVENT.getKeyCode());
        p.recycle();
    }

    @Test
    public void writeToParcel_withoutInputEvent_success() {
        Parcel p = Parcel.obtain();
        new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS).build().writeToParcel(p, 0);
        p.setDataPosition(0);
        SourceRegistrationRequest fromParcel =
                SourceRegistrationRequest.CREATOR.createFromParcel(p);
        expect.that(fromParcel.getRegistrationUris()).isEqualTo(SOURCE_REGISTRATIONS);
        expect.that(fromParcel.getInputEvent()).isNull();
        p.recycle();
    }

    @Test
    public void build_withInvalidParams_fail() {
        assertThrows(
                NullPointerException.class,
                () -> new SourceRegistrationRequest.Builder(null).setInputEvent(INPUT_KEY_EVENT));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistrationRequest.Builder(Collections.emptyList())
                                .setInputEvent(INPUT_KEY_EVENT));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistrationRequest.Builder(generateAppRegistrationUrisList(21))
                                .setInputEvent(INPUT_KEY_EVENT));

        List<Uri> listWithInvalidRegistrationUri = new ArrayList<>(SOURCE_REGISTRATIONS);
        listWithInvalidRegistrationUri.add(INVALID_REGISTRATION_URI);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistrationRequest.Builder(listWithInvalidRegistrationUri)
                                .setInputEvent(INPUT_KEY_EVENT));
    }

    @Test
    public void testDescribeContents() {
        expect.that(SOURCE_REGISTRATION_REQUEST.describeContents()).isEqualTo(0);
    }

    @Test
    public void testHashCode_equals() throws Exception {
        EqualsTester et = new EqualsTester(expect);
        SourceRegistrationRequest request1 = createExampleRegistrationRequest();
        SourceRegistrationRequest request2 = createExampleRegistrationRequest();
        et.expectObjectsAreEqual(request1, request2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        EqualsTester et = new EqualsTester(expect);
        SourceRegistrationRequest request1 = createExampleRegistrationRequest();
        SourceRegistrationRequest request2 =
                new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS)
                        .setInputEvent(null)
                        .build();
        et.expectObjectsAreNotEqual(request1, request2);
    }

    private static List<Uri> generateAppRegistrationUrisList(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Uri.parse(REGISTRATION_URI_1.toString()))
                .collect(Collectors.toList());
    }

    private static SourceRegistrationRequest createExampleRegistrationRequest() {
        return new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS)
                .setInputEvent(INPUT_KEY_EVENT)
                .build();
    }
}
