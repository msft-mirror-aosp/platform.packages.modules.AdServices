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

/** Unit tests for {@link GetTopicsParam} */
public final class GetTopicsParamTest extends AdServicesUnitTestCase {
    private static final String SOME_PACKAGE_NAME = "SomePackageName";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final String SOME_SDK_PACKAGE_NAME = "SomeSdkPackageName";

    @Test
    public void test_nonNull() {
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAppPackageName(SOME_PACKAGE_NAME)
                        .setSdkName(SOME_SDK_NAME)
                        .setSdkPackageName(SOME_SDK_PACKAGE_NAME)
                        .setShouldRecordObservation(false)
                        .build();

        expect.that(request.getSdkName()).isEqualTo(SOME_SDK_NAME);
        expect.that(request.getSdkPackageName()).isEqualTo(SOME_SDK_PACKAGE_NAME);
        expect.that(request.getAppPackageName()).isEqualTo(SOME_PACKAGE_NAME);
        expect.that(request.shouldRecordObservation()).isEqualTo(false);
    }

    @Test
    public void test_nullAppPackageName_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsParam.Builder()
                                // Not setting AppPackageName making it null.
                                .setSdkName(SOME_SDK_NAME)
                                .build());

        // Null AppPackageName.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsParam.Builder()
                                .setAppPackageName(null)
                                .setSdkName(SOME_SDK_NAME)
                                .build());
    }

    @Test
    public void test_notSettingAppPackageName_throwsIllegalArgumentException() {
        // Empty AppPackageName.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetTopicsParam.Builder()
                                .setAppPackageName("")
                                .setSdkName(SOME_SDK_NAME)
                                .build());
    }

    @Test
    public void test_notSettingRecordObservation_returnDefault() {
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAppPackageName(SOME_PACKAGE_NAME)
                        .setSdkName(SOME_SDK_NAME)
                        .setSdkPackageName(SOME_SDK_PACKAGE_NAME)
                        .build();

        expect.that(request.getSdkName()).isEqualTo(SOME_SDK_NAME);
        expect.that(request.getSdkPackageName()).isEqualTo(SOME_SDK_PACKAGE_NAME);
        expect.that(request.getAppPackageName()).isEqualTo(SOME_PACKAGE_NAME);
        // Not setting RecordObservation will get default value.
        expect.that(request.shouldRecordObservation()).isTrue();
    }
}
