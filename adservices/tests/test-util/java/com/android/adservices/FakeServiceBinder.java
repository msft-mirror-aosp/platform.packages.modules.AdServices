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

package com.android.adservices;

import android.util.Log;

import java.util.Objects;

/**
 * Fake implementation of {@link ServiceBinder}, where {@link #getService()} will always return the
 * service passed in the constructor.
 *
 * @param <T> The type of Service Binder.
 */
public final class FakeServiceBinder<T> extends ServiceBinder<T> {

    private static final String TAG = FakeServiceBinder.class.getSimpleName();

    private final T mService;

    /** Default constructor */
    public FakeServiceBinder(T service) {
        mService = Objects.requireNonNull(service, "service cannot be null");
    }

    @Override
    public T getService() {
        Log.v(TAG, "getService() called, returning " + mService);
        return mService;
    }

    @Override
    public void unbindFromService() {
        Log.v(TAG, "unbindFromService() called, ignoring it");
    }
}
