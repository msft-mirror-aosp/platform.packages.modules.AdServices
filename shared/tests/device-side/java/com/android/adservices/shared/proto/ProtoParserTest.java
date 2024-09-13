/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.proto;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PROTO_PARSER_INVALID_PROTO_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.util.ProtoParser.parseBase64EncodedStringToProto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.util.Base64;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import com.google.protobuf.MessageLite;

import org.junit.Test;
import org.mockito.Mock;

public final class ProtoParserTest extends SharedMockitoTestCase {
    private static final ErrorCodeSampleInterval SAMPLE_RATE =
            ErrorCodeSampleInterval.newBuilder()
                    .putSampleIntervalToErrorCodes(
                            10, ErrorCodeList.newBuilder().addErrorCode(1).addErrorCode(2).build())
                    .build();

    private static final String PROPERTY_NAME = "Property";

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    @Test
    public void parseBase64EncodedStringToProto_success() {
        String encodedStr = getBase64EncodedString(SAMPLE_RATE, Base64.DEFAULT);

        ErrorCodeSampleInterval actual =
                parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, encodedStr);

        expect.that(actual).isNotNull();
        expect.that(actual).isEqualTo(SAMPLE_RATE);
    }

    @Test
    public void parseBase64EncodedStringToProto_noWrapNoPadding_success() {
        String encodedStr = getBase64EncodedString(SAMPLE_RATE, Base64.NO_WRAP | Base64.NO_PADDING);

        ErrorCodeSampleInterval actual =
                parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, encodedStr);

        expect.that(actual).isNotNull();
        expect.that(actual).isEqualTo(SAMPLE_RATE);
    }

    @Test
    public void parseBase64EncodedStringToProto_incorrectEncodedString() {
        ErrorCodeSampleInterval actual =
                parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(),
                        mMockAdServicesErrorLogger,
                        PROPERTY_NAME,
                        /* value= */ "xyz");

        expect.that(actual).isNull();
        verify(mMockAdServicesErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(
                                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PROTO_PARSER_INVALID_PROTO_ERROR),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON));
    }

    @Test
    public void parseBase64EncodedStringToProto_emptyEncodedString() {
        ErrorCodeSampleInterval actual =
                parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, /* value= */ "");

        expect.that(actual).isNull();
    }

    @Test
    public void parseBase64EncodedStringToProto_nullEncodedString() {
        ErrorCodeSampleInterval actual =
                parseBase64EncodedStringToProto(
                        ErrorCodeSampleInterval.parser(), PROPERTY_NAME, /* value= */ null);

        expect.that(actual).isNull();
    }

    // Converts proto to a Base64 encoded string.
    private static <T extends MessageLite> String getBase64EncodedString(T value, int flag) {
        return Base64.encodeToString(value.toByteArray(), flag);
    }
}
