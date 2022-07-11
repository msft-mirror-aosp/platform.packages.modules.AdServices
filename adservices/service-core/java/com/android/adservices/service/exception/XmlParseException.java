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

package com.android.adservices.service.exception;

import android.annotation.NonNull;

/** Exception thrown when there is an error parsing the Safety Center configuration. */
public final class XmlParseException extends Exception {

    public XmlParseException(@NonNull String message) {
        super(message);
    }

    public XmlParseException(@NonNull String message, @NonNull Throwable ex) {
        super(message, ex);
    }
}
