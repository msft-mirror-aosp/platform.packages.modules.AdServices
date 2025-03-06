/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.data.configdelivery;

import androidx.room.TypeConverter;

import com.android.adservices.LogUtil;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Converters for Configuration Database.
 *
 * <p>More details <a
 * href="https://developer.android.com/training/data-storage/room/referencing-data">here</a>.
 */
public class Converters {
    @TypeConverter
    public int fromConfigurationType(ConfigurationType configurationType) {
        return configurationType.getNumber();
    }

    @TypeConverter
    public ConfigurationType toConfigurationType(int configurationType) {
        return ConfigurationType.forNumber(configurationType);
    }

    @TypeConverter
    public byte[] fromAny(Any any) {
        if (any == null || any.equals(Any.getDefaultInstance())) {
            return null;
        }
        return any.toByteArray();
    }

    @TypeConverter
    public Any toAny(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return Any.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            // TODO: b/397634660 - Add a ErrorLogUtil log
            LogUtil.e(e, "Unable to parse bytes into com.google.protobuf.Any");
            return null;
        }
    }
}
