/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.common;

import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.util.Objects;

public class ProtoParserUtil {

    private ProtoParserUtil() {}

    @Nullable
    public static <T> T fromBase64(@Nullable String base64EncodedProto, Parser<T> parser) {

        if (TextUtils.isEmpty(base64EncodedProto)) {
            LogUtil.d("Base64 value is empty.");
            return null;
        }

        try {
            final byte[] decode = Base64.decode(
                            base64EncodedProto, Base64.NO_PADDING | Base64.NO_WRAP);
            if (Objects.isNull(decode)) {
                LogUtil.d("Decoded Base64 value is null.");
                return null;
            }
            return parser.parseFrom(decode);
        } catch (Exception e) {
            LogUtil.e(e, "Error while parsing Base64 string");
            return null;
        }
    }

    public static String toBase64(MessageLite messageLite) {
        return Base64.encodeToString(
                messageLite.toByteArray(), Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
