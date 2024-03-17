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

package android.app.sdksandbox;

// TODO(b/304459399): remove the class and use autogenerated values once they can be used in
// framework
/**
 * Utility class for StatsD logging events.
 *
 * @hide
 */
public class StatsdUtil {
    // Values for SandboxActivityEventOccurred.method
    public static final int
            SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__REGISTER_SDK_SANDBOX_ACTIVITY_HANDLER = 1;
    public static final int
            SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__UNREGISTER_SDK_SANDBOX_ACTIVITY_HANDLER = 2;
    public static final int
            SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__PUT_SDK_SANDBOX_ACTIVITY_HANDLER = 3;
    public static final int
            SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__REMOVE_SDK_SANDBOX_ACTIVITY_HANDLER = 4;
    public static final int SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY = 5;
    public static final int
            SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__NOTIFY_SDK_ON_ACTIVITY_CREATION = 9;

    // Values for SandboxActivityEventOccurred.call_result
    public static final int SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS = 1;
    public static final int SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE = 2;
}
