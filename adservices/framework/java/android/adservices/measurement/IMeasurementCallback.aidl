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

/**
  * Interface for a Measurement API result callback.
  *
  * @hide
  */
oneway interface IMeasurementCallback {
    const int RESULT_OK = 0;
    const int RESULT_INTERNAL_ERROR = 1;
    const int RESULT_INVALID_ARGUMENT = 2;
    const int RESULT_IO_ERROR = 3;
    const int RESULT_RATE_LIMIT_REACHED = 4;

    /**
     * Callback invoked when a Measurement API request completes.
     *
     * @param result A RESULT_* value above.
     * @hide
     */
    void onResult(int result);
}
