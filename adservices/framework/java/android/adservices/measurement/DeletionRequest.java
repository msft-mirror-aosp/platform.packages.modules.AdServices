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

package android.adservices.measurement;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import java.time.Instant;

/** Get Deletion Request. */
public class DeletionRequest {
    private final Uri mOriginUri;
    private final Instant mStart;
    private final Instant mEnd;

    private DeletionRequest(
            @Nullable Uri originUri, @Nullable Instant start, @Nullable Instant end) {
        mOriginUri = originUri;
        mStart = start;
        mEnd = end;
    }

    /** Get the origin URI. */
    @Nullable
    public Uri getOriginUri() {
        return mOriginUri;
    }

    /** Get the start of the deletion range. */
    @Nullable
    public Instant getStart() {
        return mStart;
    }

    /** Get the end of the deletion range. */
    @Nullable
    public Instant getEnd() {
        return mEnd;
    }

    /** Builder for {@link DeletionRequest} objects. */
    public static final class Builder {
        private Uri mOriginUri;
        private Instant mStart;
        private Instant mEnd;

        public Builder() {}

        /** Set the origin URI (the android source package or eTLD+1 to delete data for). */
        public @NonNull Builder setOriginUri(@Nullable Uri originUri) {
            mOriginUri = originUri;
            return this;
        }

        /** Set the start of the deletion range. */
        public @NonNull Builder setStart(@Nullable Instant start) {
            mStart = start;
            return this;
        }

        /** Set the end of the deletion range. */
        public @NonNull Builder setEnd(@Nullable Instant end) {
            mEnd = end;
            return this;
        }

        /** Builds a {@link DeletionRequest} instance. */
        public @NonNull DeletionRequest build() {
            return new DeletionRequest(mOriginUri, mStart, mEnd);
        }
    }
}
