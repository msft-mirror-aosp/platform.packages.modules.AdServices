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

package com.android.adservices.service.measurement;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.RegistrationRequest;
import android.annotation.NonNull;
import android.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;


/**
 *
 * <p>This class is thread safe.
 * @hide
 */
@ThreadSafe
@WorkerThread
public final class MeasurementImpl {
    private static final String TAG = "MeasurementImpl";
    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
    private final SourceFetcher mSourceFetcher = new SourceFetcher();
    private final TriggerFetcher mTriggerFetcher = new TriggerFetcher();

    private static volatile MeasurementImpl sMeasurementImpl;

    /**
     * Gets an instance of MeasurementImpl to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static MeasurementImpl getInstance() {
        if (sMeasurementImpl == null) {
            synchronized (MeasurementImpl.class) {
                if (sMeasurementImpl == null) {
                    sMeasurementImpl = new MeasurementImpl();
                }
            }
        }
        return sMeasurementImpl;
    }

    /**
     * Implement a registration request, returning a result code.
     */
    public int register(@NonNull RegistrationRequest request) {
        mReadWriteLock.readLock().lock();
        try {
            switch (request.getRegistrationType()) {
                case RegistrationRequest.REGISTER_SOURCE: {
                    ArrayList<SourceRegistration> results = new ArrayList();
                    boolean success = mSourceFetcher.fetchSource(request, results);
                    // TODO: Do something with results.
                    if (success) {
                        return IMeasurementCallback.RESULT_OK;
                    } else {
                        return IMeasurementCallback.RESULT_IO_ERROR;
                    }
                }

                case RegistrationRequest.REGISTER_TRIGGER: {
                    ArrayList<TriggerRegistration> results = new ArrayList();
                    boolean success = mTriggerFetcher.fetchTrigger(request, results);
                    // TODO: Do something with results.
                    if (success) {
                        return IMeasurementCallback.RESULT_OK;
                    } else {
                        return IMeasurementCallback.RESULT_IO_ERROR;
                    }
                }

                default:
                    return IMeasurementCallback.RESULT_INVALID_ARGUMENT;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Implement a deleteRegistrations request, returning a result code.
     */
    public int deleteRegistrations(@NonNull DeletionRequest request) {
        mReadWriteLock.readLock().lock();
        try {
            // TODO: Implement!
            return IMeasurementCallback.RESULT_OK;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }
}
