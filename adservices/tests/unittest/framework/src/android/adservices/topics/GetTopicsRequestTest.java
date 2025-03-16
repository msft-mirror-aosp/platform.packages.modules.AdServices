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

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link android.adservices.topics.GetTopicsRequest} */
public final class GetTopicsRequestTest extends AdServicesUnitTestCase {
    private static final String SOME_SDK_NAME = "SomeSDKName";

    @Test
    public void testBuilder_notSettingSdkName() {
        GetTopicsRequest request = new GetTopicsRequest.Builder().build();
        expect.that(request.getAdsSdkName()).isEmpty();
        // RecordObservation default value is true
        expect.that(request.shouldRecordObservation()).isTrue();
    }

    @Test
    public void testBuilderSetAdsSdkName_nullSdkName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GetTopicsRequest.Builder().setAdsSdkName(/* adsSdkName */ null).build());
    }

    @Test
    public void testBuilderSetAdsSdkName_nonNullSdkName() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder()
                        .setAdsSdkName(/* adsSdkName */ SOME_SDK_NAME)
                        .build();
        expect.that(request.getAdsSdkName()).isEqualTo(SOME_SDK_NAME);
        // RecordObservation default value is true
        expect.that(request.shouldRecordObservation()).isTrue();
    }

    @Test
    public void testBuilderSetAdsSdkName_recordObservationFalse() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder()
                        .setAdsSdkName(/* adsSdkName */ SOME_SDK_NAME)
                        .setShouldRecordObservation(false)
                        .build();
        expect.that(request.getAdsSdkName()).isEqualTo(SOME_SDK_NAME);
        expect.that(request.shouldRecordObservation()).isFalse();
    }

    @Test
    public void testBuilder_recordObservationFalse() {
        GetTopicsRequest request =
                new GetTopicsRequest.Builder().setShouldRecordObservation(false).build();
        expect.that(request.getAdsSdkName()).isEmpty();
        expect.that(request.shouldRecordObservation()).isFalse();
    }
}
