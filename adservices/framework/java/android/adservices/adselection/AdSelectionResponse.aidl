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

package android.adservices.adselection;

/**
* This represents the result of the
* {@link AdSelectionService#runAdSelection(AdSelectionConfig,AdSelectionCallback)} call returned to
* the {@link AdSelectionCallback}. It contains:

 <ul>
   <li>  {@link AdSelectionResponse#getResultCode()} indicates the status. {@link AdSelectionResponse#RESULT_OK} represent a successful completion.
   <li>  {@link AdSelectionResponse#getErrorMessage()} describes the failure. It is {@code null} for a successful run and non-null otherwise.
   <li> {@link AdSelectionResponse#getAdSelectionId()}: which uniquely identifies a successful ad selection event. It is non-zero in case of successful completion and zero otherwise.
   <li> {@link AdSelectionResponse#getAdData()} for the selected winning Ads for a successful ad selection run. This value is {@code null} for an unsuccessful ad selection.
 </ul>
*/
parcelable AdSelectionResponse;