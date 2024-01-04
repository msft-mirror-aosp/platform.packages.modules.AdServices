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

package android.adservices.signals;

import android.adservices.common.FledgeErrorResponse;

/**
 * Callback class for the updateSignals API operation.
 *
 * @hide
 */
oneway interface UpdateSignalsCallback {
    /**
     * Sends back a void indicating success.
     */
    void onSuccess();
    /**
     * Sends back a status code and error message indicating failure.
     */
    void onFailure(in FledgeErrorResponse responseParcel);
}
