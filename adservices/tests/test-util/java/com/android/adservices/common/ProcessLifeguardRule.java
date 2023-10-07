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
package com.android.adservices.common;

import android.os.Looper;

/**
 * Rule used to protect the test process from crashing if an uncaught exception is thrown in the
 * background.
 */
public final class ProcessLifeguardRule extends AbstractProcessLifeguardRule {

    public ProcessLifeguardRule() {
        super(AndroidLogger.getInstance());
    }

    @Override
    protected boolean isMainThread() {
        return Looper.getMainLooper().isCurrentThread();
    }
}
