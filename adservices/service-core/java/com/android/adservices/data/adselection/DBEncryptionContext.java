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

package com.android.adservices.data.adselection;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;
import com.android.adservices.ohttp.EncapsulatedSharedSecret;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;

import com.google.auto.value.AutoValue;

/** Table representing Encryption Context. */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "encryption_context",
        primaryKeys = {"context_id", "encryption_key_type"})
@TypeConverters({FledgeRoomConverters.class})
public abstract class DBEncryptionContext {

    /** The id associated with this encryption context. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "context_id")
    public abstract long getContextId();

    /** The key type associated with this encryption context. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "encryption_key_type")
    @EncryptionKeyConstants.EncryptionKeyType
    public abstract int getEncryptionKeyType();

    /** The key config associated with this encryption context. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "key_config")
    public abstract ObliviousHttpKeyConfig getKeyConfig();

    /** The encapsulated shared secret generated for this context. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "shared_secret")
    public abstract EncapsulatedSharedSecret getSharedSecret();

    /** The seed bytes that will be used to regenerate HPKE context. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "seed")
    public abstract String getSeed();

    /**
     * Returns an AutoValue builder for a {@link
     * com.android.adservices.data.adselection.DBEncryptionContext} entity.
     */
    public static DBEncryptionContext.Builder builder() {
        return new AutoValue_DBEncryptionContext.Builder();
    }

    /** Creates a {@link DBEncryptionContext} using the builder. */
    public static DBEncryptionContext create(
            long contextId,
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType,
            ObliviousHttpKeyConfig keyConfig,
            EncapsulatedSharedSecret sharedSecret,
            String seed) {
        return builder()
                .setContextId(contextId)
                .setEncryptionKeyType(encryptionKeyType)
                .setKeyConfig(keyConfig)
                .setSharedSecret(sharedSecret)
                .setSeed(seed)
                .build();
    }

    /** Builder class for a {@link com.android.adservices.data.adselection.DBEncryptionContext}. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets ad selection id. */
        public abstract Builder setContextId(long contextId);

        /** Sets encryption key type. */
        public abstract Builder setEncryptionKeyType(
                @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType);

        /** Key Config associated with this encryption context. */
        public abstract Builder setKeyConfig(ObliviousHttpKeyConfig keyConfig);

        /** Encapsulated shared secret for this encryption context. */
        public abstract Builder setSharedSecret(EncapsulatedSharedSecret sharedSecret);

        /** The seed required to regenerate HPKE context. */
        public abstract Builder setSeed(String seed);

        /** Builds the DBEncryptionContext. */
        public abstract DBEncryptionContext build();
    }
}
