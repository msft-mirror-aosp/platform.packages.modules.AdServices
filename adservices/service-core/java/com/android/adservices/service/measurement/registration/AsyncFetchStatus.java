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

package com.android.adservices.service.measurement.registration;

/** POJO for storing source and trigger fetcher status */
public class AsyncFetchStatus {
    public enum ResponseStatus {
        SUCCESS,
        SERVER_UNAVAILABLE,
        NETWORK_ERROR,
        PARSING_ERROR,
        INVALID_ENROLLMENT
    }

    private ResponseStatus mStatus;

    public AsyncFetchStatus() {
        mStatus = ResponseStatus.SERVER_UNAVAILABLE;
    }

    /** Get the status of a communication with an Ad Tech server. */
    public ResponseStatus getStatus() {
        return mStatus;
    }

    /**
     * Set the status of a communication with an Ad Tech server.
     *
     * @param status a {@link ResponseStatus} that is used up the call stack to determine errors in
     *     the Ad tech server during source and trigger fetching.
     */
    public void setStatus(ResponseStatus status) {
        mStatus = status;
    }
}
