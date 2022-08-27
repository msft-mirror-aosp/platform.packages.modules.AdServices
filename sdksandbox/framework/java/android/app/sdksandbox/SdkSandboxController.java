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

package android.app.sdksandbox;

/**
 * Controller that is used by SDK loaded in the sandbox to access APIs provided by the sdk sandbox.
 *
 * <p>This controller holds all APIs the sdk would need to get information from or pass information
 * to the sdk sandbox.
 *
 * <p>It enables the SDK to communicate with other SDKS in the SDK sandbox and know about the state
 * of the sdks that are currently loaded in it.
 *
 * <p>An instance of the {@link SdkSandboxController} will be created by the SDK sandbox, and then
 * attached to the {@link SandboxedSdkProvider} when it was created.
 */
public interface SdkSandboxController {}
