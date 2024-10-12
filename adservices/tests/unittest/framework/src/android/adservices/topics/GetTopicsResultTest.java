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
package android.adservices.topics;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.util.List;

/** Unit tests for {@link GetTopicsResult} */
public final class GetTopicsResultTest extends AdServicesUnitTestCase {

    private static final byte[] BYTE_ARRAY_1 = new byte[] {1, 2, 3};
    private static final byte[] BYTE_ARRAY_2 = new byte[] {4, 5, 6};
    private static final byte[] BYTE_ARRAY_3 = new byte[] {7, 8, 9};
    private static final byte[] BYTE_ARRAY_4 = new byte[] {10, 11, 12};

    @Test
    public void testWriteToParcel() throws Exception {
        GetTopicsResult response =
                new GetTopicsResult.Builder()
                        .setTaxonomyVersions(List.of(1L, 2L))
                        .setModelVersions(List.of(3L, 4L))
                        .setTopics(List.of(1, 2))
                        .setEncryptedTopics(List.of(BYTE_ARRAY_1, BYTE_ARRAY_2))
                        .setEncryptionKeys(List.of("Key1", "Key2"))
                        .setEncapsulatedKeys(List.of(BYTE_ARRAY_3, BYTE_ARRAY_4))
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        GetTopicsResult fromParcel = GetTopicsResult.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getTaxonomyVersions()).containsExactly(1L, 2L).inOrder();
        expect.that(fromParcel.getModelVersions()).containsExactly(3L, 4L).inOrder();
        expect.that(fromParcel.getTopics()).containsExactly(1, 2).inOrder();
        expect.that(fromParcel.getEncryptedTopics().get(0)).isEqualTo(BYTE_ARRAY_1);
        expect.that(fromParcel.getEncryptedTopics().get(1)).isEqualTo(BYTE_ARRAY_2);
        expect.that(fromParcel.getEncryptionKeys()).containsExactly("Key1", "Key2").inOrder();
        expect.that(fromParcel.getEncapsulatedKeys().get(0)).isEqualTo(BYTE_ARRAY_3);
        expect.that(fromParcel.getEncapsulatedKeys().get(1)).isEqualTo(BYTE_ARRAY_4);
    }

    @Test
    public void testWriteToParcel_emptyResponse() {
        GetTopicsResult response = new GetTopicsResult.Builder().build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        GetTopicsResult fromParcel = GetTopicsResult.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getTaxonomyVersions()).isEmpty();
        expect.that(fromParcel.getModelVersions()).isEmpty();
        expect.that(fromParcel.getTopics()).isEmpty();
        expect.that(fromParcel.getEncryptedTopics()).isEmpty();
        expect.that(fromParcel.getEncryptionKeys()).isEmpty();
        expect.that(fromParcel.getEncapsulatedKeys()).isEmpty();
    }

    @Test
    public void testWriteToParcel_nullableThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GetTopicsResult.Builder().setTopics(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> new GetTopicsResult.Builder().setEncryptedTopics(null).build());

        // This should not throw.
        new GetTopicsResult.Builder()
                // Not setting anything default to empty.
                .build();

        expect.that(
                        new GetTopicsResult.Builder()
                                // Not setting anything for encrypted topics list
                                .setTopics(List.of())
                                .build()
                                .getTopics())
                .isEmpty();
        expect.that(
                        new GetTopicsResult.Builder()
                                // Not setting anything for topics list
                                .setEncryptedTopics(List.of())
                                .build()
                                .getEncryptedTopics())
                .isEmpty();
    }

    @Test
    public void testWriteToParcel_misMatchSizeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsResult.Builder()
                                .setTaxonomyVersions(List.of(1L))
                                .setModelVersions(List.of(3L, 4L))
                                .setTopics(List.of(1, 2))
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsResult.Builder()
                                // Not setting TaxonomyVersions implies empty.
                                .setModelVersions(List.of(3L, 4L))
                                .setTopics(List.of(1, 2))
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsResult.Builder()
                                .setTaxonomyVersions(List.of(1L, 2L))
                                .setModelVersions(List.of(3L, 4L))
                                .setTopics(List.of(1))
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsResult.Builder()
                                .setEncryptedTopics(List.of(BYTE_ARRAY_1, BYTE_ARRAY_2))
                                .setEncryptionKeys(List.of("Key1", "Key2", "Key3"))
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsResult.Builder()
                                .setEncryptedTopics(List.of(BYTE_ARRAY_1, BYTE_ARRAY_2))
                                .setEncryptionKeys(List.of("Key1", "Key2"))
                                .setEncapsulatedKeys(List.of())
                                .build());
    }
}
