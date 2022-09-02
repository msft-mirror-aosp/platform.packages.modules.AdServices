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


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.adservices.topics.GetTopicsRequest}
 */
@SmallTest
public final class GetTopicsRequestTest {
    private static final String SOME_SDK_NAME = "SomeSDKName";

    @Test
    public void testCreate() {
        GetTopicsRequest request = GetTopicsRequest.create();
        assertNull(request.getAdsSdkName());
    }

    @Test
    public void testCreateWithAdsSdkName_nullSdkName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsRequest.createWithAdsSdkName(/* adsSdkName */ null);
                });
    }

    @Test
    public void testCreateWithAdsSdkName_nonNullSdkName() {
        GetTopicsRequest request =
                GetTopicsRequest.createWithAdsSdkName(/* adsSdkName */ SOME_SDK_NAME);
        assertThat(request.getAdsSdkName()).isEqualTo(SOME_SDK_NAME);
    }
}
