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

package com.android.adservices.data.signals;

import android.adservices.common.CommonFixture;

public class DBEncodedPayloadFixture {

    public static final byte[] SAMPLE_PAYLOAD = {(byte) 10, (byte) 20, (byte) 30, (byte) 40};

    public static DBEncodedPayload anEncodedPayload() {
        return anEncodedPayloadBuilder().build();
    }

    public static DBEncodedPayload.Builder anEncodedPayloadBuilder() {
        return DBEncodedPayload.builder()
                .setBuyer(CommonFixture.VALID_BUYER_1)
                .setVersion(1)
                .setCreationTime(CommonFixture.FIXED_NOW)
                .setEncodedPayload(SAMPLE_PAYLOAD);
    }
}
