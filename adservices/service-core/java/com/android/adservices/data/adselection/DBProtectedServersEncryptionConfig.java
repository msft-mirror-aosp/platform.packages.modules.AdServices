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

package com.android.adservices.data.adselection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;

import java.time.Instant;

/**
 * Table representing Encryption keys with a field for coordinator to specify where the key was
 * fetched from.
 */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "protected_servers_encryption_config",
        indices = {
            @Index(value = {"coordinator_url", "encryption_key_type", "expiry_instant"}),
            @Index(
                    value = {"coordinator_url", "encryption_key_type", "key_identifier"},
                    unique = true)
        })
public abstract class DBProtectedServersEncryptionConfig {

    /**
     * Unique ID per key record. This is different from {@link #getKeyIdentifier()} which is only
     * unique per coordinator.
     *
     * <p>This ID is only used internally in the table and does not need to be stable or
     * reproducible. It is auto-generated by Room if set to {@code null} on insertion.
     */
    @AutoValue.CopyAnnotations
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    @Nullable
    public abstract Long getRowId();

    /** The coordinator URL from which this key was fetched. */
    @NonNull
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "coordinator_url")
    public abstract String getCoordinatorUrl();

    /** Type of Key. */
    @NonNull
    @AutoValue.CopyAnnotations
    @EncryptionKeyConstants.EncryptionKeyType
    @ColumnInfo(name = "encryption_key_type")
    public abstract int getEncryptionKeyType();

    /** KeyIdentifier used for versioning the keys. */
    @NonNull
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "key_identifier")
    public abstract String getKeyIdentifier();

    /**
     * The actual public key. Encoding and parsing of this key is dependent on the keyType and will
     * be managed by the Key Client.
     */
    @NonNull
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "public_key")
    public abstract String getPublicKey();

    /** Instant this EncryptionKey entry was created. */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_instant")
    public abstract Instant getCreationInstant();

    /**
     * Expiry TTL for this encryption key in seconds. This is sent by the server and stored on
     * device for computing expiry Instant. Clients should directly read the expiryInstant unless
     * they specifically need to know the expiry ttl seconds reported by the server.
     */
    @NonNull
    @AutoValue.CopyAnnotations
    public abstract Long getExpiryTtlSeconds();

    /**
     * Expiry Instant for this encryption key computed as
     * creationInstant.plusSeconds(expiryTtlSeconds). Clients should use this field to read the key
     * expiry value instead of computing it from creation instant and expiry ttl seconds.
     */
    @NonNull
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "expiry_instant")
    public abstract Instant getExpiryInstant();

    /** Returns an AutoValue builder for a {@link DBProtectedServersEncryptionConfig} entity. */
    @NonNull
    public static DBProtectedServersEncryptionConfig.Builder builder() {
        return new AutoValue_DBProtectedServersEncryptionConfig.Builder();
    }

    /**
     * Creates a {@link DBProtectedServersEncryptionConfig} object using the builder.
     *
     * <p>Required for Room SQLite integration.
     */
    @NonNull
    public static DBProtectedServersEncryptionConfig create(
            Long rowId,
            @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType,
            String coordinatorUrl,
            String keyIdentifier,
            String publicKey,
            Instant creationInstant,
            Long expiryTtlSeconds,
            Instant expiryInstant) {

        return builder()
                .setRowId(rowId)
                .setEncryptionKeyType(encryptionKeyType)
                .setCoordinatorUrl(coordinatorUrl)
                .setKeyIdentifier(keyIdentifier)
                .setPublicKey(publicKey)
                .setCreationInstant(creationInstant)
                .setExpiryInstant(expiryInstant)
                .setExpiryTtlSeconds(expiryTtlSeconds)
                .build();
    }

    /** Builder class for a {@link DBProtectedServersEncryptionConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {

        /**
         * Sets the unique ID per key record. This is different from {@link #getKeyIdentifier()}
         * which is only unique per coordinator.
         *
         * <p>This ID is only used internally in the table and does not need to be stable or
         * reproducible. It is auto-generated by Room if set to {@code null} on insertion.
         */
        abstract Builder setRowId(Long rowId);

        /** Sets encryption key tupe. */
        public abstract Builder setEncryptionKeyType(
                @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType);

        /** Identifier used to identify the encryptionKey. */
        public abstract Builder setKeyIdentifier(String keyIdentifier);

        /** Sets the coordinator URL from which the key is fetched. */
        public abstract Builder setCoordinatorUrl(String coordinatorUrl);

        /** Public key of an asymmetric key pair represented by this encryptionKey. */
        public abstract Builder setPublicKey(String publicKey);

        /** Ttl in seconds for the EncryptionKey. */
        public abstract Builder setExpiryTtlSeconds(Long expiryTtlSeconds);

        /** Creation instant for the key. */
        abstract Builder setCreationInstant(Instant creationInstant);

        /** Expiry instant for the key. */
        abstract Builder setExpiryInstant(Instant expiryInstant);

        abstract Long getExpiryTtlSeconds();

        abstract DBProtectedServersEncryptionConfig autoBuild();

        /** Builds the key based on the set values after validating the input. */
        public final DBProtectedServersEncryptionConfig build() {
            // TODO(b/284445328): Set creation Instant as the instant key was fetched.
            // This would allow accurate computation of expiry instant as fetchInstant + maxage.
            Instant creationInstant = Instant.now();
            setCreationInstant(creationInstant);
            setExpiryInstant(creationInstant.plusSeconds(getExpiryTtlSeconds()));

            return autoBuild();
        }
    }
}