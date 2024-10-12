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
package com.android.adservices.shared.testing.concurrency;

import com.android.adservices.shared.testing.Identifiable;

/* Usage examples (not in the javadoc because of the <> inside)

   // Result-less
   SimpleSyncCallback callback = new SimpleSyncCallback();
   mExecutor.execute(() -> callback.setCalled());
   callback.assertCalled();

   // Result-oriented
   ResultSyncCallback<String> callback = new ResultSyncCallback<>();
   mExecutor.execute(() -> callback.injectResult("dqw4w9wxcq"));
   String videoId = callback.assertResultReceived();
   expect.withMessage("video id").that(videoId).isEqualTo("dqw4w9wxcq!");

   // Internal
   AnswerSyncCallback<Void> answer = AnswerSyncCallback.forVoidAnswer();
   doAnswer(answer).when(mMockExecutor).execute(any());
   mMockExecutor.execute(null);
   answer.assertCalled();
*/

/**
 * Base interface for all sync callbacks.
 *
 * <p>A {@code SyncCallback} has 2 types of methods, which are used to:
 *
 * <ol>
 *   <li>Block the test until the callback is called.
 *   <li>Unblock the test (i.e., call the callback).
 * </ol>
 *
 * <p>Hence, it's typical usage in a test method is:
 *
 * <ol>
 *   <li>Gets a callback.
 *   <li>Do something in the background that calls the callback.
 *   <li>Call a callback method that will block the test until the callback is called.
 * </ol>
 *
 * <p>The first type includes {@link #assertCalled()}, although subclasses can provide other methods
 * that are more specialized (like {@code assertResultReceived()} and/or {@code
 * assertFailureReceived()}.
 *
 * <p>The second type depends on the callback, but it can be divided in 3 categories:
 *
 * <ol>
 *   <li>Methods that don't take a result.
 *   <li>Methods that take a result.
 *   <li>Internal methods (i.e., they're not called by tests, but internally by the callback).
 * </ol>
 *
 * <p>See a few concrete examples in the code, above the class javadoc...
 */
public interface SyncCallback extends Identifiable {

    /** Tag used on {@code logcat} calls. */
    String LOG_TAG = "SyncCallback";

    /**
     * Asserts the callback was called or throw if it times out - the timeout value is defined by
     * the constructor and can be obtained through {@link #getSettings()}.
     */
    void assertCalled() throws InterruptedException;

    /** Returns whether the callback was called (at least) the expected number of times. */
    boolean isCalled();

    /** Gets the total number of calls so far. */
    int getNumberActualCalls();

    /** Gets the callback settings. */
    SyncCallbackSettings getSettings();
}
