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

import static android.adservices.topics.TopicsManager.EMPTY_SDK;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link android.adservices.topics.GetTopicsRequest}
 */
@SmallTest
public final class GetTopicsRequestTest {

    private static final String SOME_SDK_NAME = "SomeSDKName";

    @Test
    public void testNonNullSdkName() {
        GetTopicsRequest request =
            new GetTopicsRequest.Builder().setSdkName(SOME_SDK_NAME).build();

        assertThat(request.getSdkName()).isEqualTo(SOME_SDK_NAME);
    }

    @Test
    public void testNullSdkName() {
        GetTopicsRequest request =
            new GetTopicsRequest.Builder()
                // Not setting mSdkName making it null.
                .build();
        // When sdkName is not set in builder, we will use EMPTY_SDK by default
        assertThat(request.getSdkName()).isEqualTo(EMPTY_SDK);
    }
}
