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

package com.android.adservices.service.common.cache;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.auto.value.AutoValue;

import java.time.Instant;

/** An entry that can be cached, for this class it contains a url and its response body */
@AutoValue
@AutoValue.CopyAnnotations
@Entity(
        tableName = "http_cache",
        indices = {@Index(value = {"cache_url"})})
public abstract class DBCacheEntry {

    /** @return Provides the URL which is the primary key for cached entry */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "cache_url")
    @NonNull
    @PrimaryKey
    public abstract String getUrl();

    /** @return the response body corresponding to the cached url */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "response_body")
    public abstract String getResponseBody();

    /** @return the timestamp at which this entry was cached */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "creation_timestamp")
    public abstract Instant getCreationTimestamp();

    /** @return max time in second for which this entry should be considered fresh */
    @AutoValue.CopyAnnotations
    @ColumnInfo(name = "max_age")
    public abstract long getMaxAgeSeconds();

    /**
     * Creates an entry that can be persisted in the cache storage
     *
     * @param url for which the request needs to be cached
     * @param responseBody response for the url request made
     * @param creationTimestamp time at which the request is persisted
     * @param maxAgeSeconds time for which this cache entry is considered fresh
     * @return an instance or created {@link DBCacheEntry}
     */
    public static DBCacheEntry create(
            @NonNull String url,
            String responseBody,
            Instant creationTimestamp,
            long maxAgeSeconds) {
        return builder()
                .setUrl(url)
                .setResponseBody(responseBody)
                .setCreationTimestamp(creationTimestamp)
                .setMaxAgeSeconds(maxAgeSeconds)
                .build();
    }

    /** @return a builder to construct an instance of {@link DBCacheEntry} */
    public static DBCacheEntry.Builder builder() {
        return new AutoValue_DBCacheEntry.Builder();
    }

    /** Provides a builder for creating a {@link DBCacheEntry} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the Url for which the entry is cached */
        public abstract DBCacheEntry.Builder setUrl(String url);

        /** sets the response body corresponding to the URL */
        public abstract DBCacheEntry.Builder setResponseBody(String responseBody);

        /** Sets the creation timestamp of the cached entry */
        public abstract DBCacheEntry.Builder setCreationTimestamp(Instant creationTimestamp);

        /** Sets the maxAge in seconds for which the entry is considered fresh */
        public abstract DBCacheEntry.Builder setMaxAgeSeconds(long maxAgeSeconds);

        /**
         * Returns a {@link com.android.adservices.service.common.cache.DBCacheEntry} build with the
         * information provided in this builder *
         */
        public abstract DBCacheEntry build();
    }
}
