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
package com.android.adservices.service.common;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.io.PrintWriter;
import java.util.Objects;

/** Custom application context for AdServices. */
public final class AdServicesApplicationContext extends ContextWrapper {

    AdServicesApplicationContext(Context context) {
        super(Objects.requireNonNull(context, "context cannot be null"));
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, @Nullable String[] args) {
        String prefix = "  ";

        writer.printf("%sbaseContext: %s\n", prefix, getBaseContext());
        writer.printf("%sapplicationContext: %s\n", prefix, getApplicationContext());
        if (SdkLevel.isAtLeastS()) { // TODO(b/371467118): remove once project set for S+
            writer.printf("%suser: %s\n", prefix, getUser());
        }
        writer.printf("%sdataDir: %s\n", prefix, getDataDir());
    }
}
