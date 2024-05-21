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
package com.android.adservices.shared.concurrency;

import java.util.concurrent.TimeUnit;

/**
 * Abstraction to block asynchronous operations until they're completed.
 *
 * <p><b>Note: </b>it doesn't specifies how many times the callback is expected to be called, that's
 * up to the implementation.
 */
public interface SyncCallback {

    /**
     * Indicates the callback was called, so it unblocks {@link #waitCalled()} / {@link
     * #waitCalled(long, TimeUnit)}.
     */
    void setCalled();

    /**
     * Wait (indefinitely) until all calls to {@link #setCalled()} were made.
     *
     * @throws InterruptedException if thread was interrupted while waiting.
     */
    void waitCalled() throws InterruptedException;

    // TODO(b/337014024); throw specific subclass
    /**
     * Wait (up to given time) until all calls to {@link #setCalled()} were made.
     *
     * @throws InterruptedException if thread was interrupted while waiting.
     * @throws IllegalStateException if not called before it timed out.
     */
    void waitCalled(long timeout, TimeUnit unit) throws InterruptedException;

    /** Returns whether the callback was called (at least) the expected number of times. */
    boolean isCalled();
}
