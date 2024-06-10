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
package com.android.adservices.shared.testing;

import com.android.adservices.shared.testing.Logger.RealLogger;

import com.google.common.truth.Expect;

import org.junit.Rule;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Uber" superclass for all tests.
 *
 * <p>It provide the bare minimum features that will be used by all sort of tests (unit / CTS,
 * device/host side, project-specific).
 */
public abstract class SidelessTestCase implements TestNamer {

    private static final AtomicInteger sNextInvocationId = new AtomicInteger();

    // TODO(b/342639109): set order
    @Rule public final Expect expect = Expect.create();

    private final int mInvocationId = sNextInvocationId.incrementAndGet();

    // TODO(b/285014040): log test number / to String on constructor (will require logV()).
    // Something like (which used to be on AdServicesTestCase):
    // Log.d(TAG, "setTestNumber(): " + getTestName() + " is test #" + mTestNumber);

    protected final Logger mLogger;
    protected final RealLogger mRealLogger;

    public SidelessTestCase() {
        this(StandardStreamsLogger.getInstance());
    }

    public SidelessTestCase(RealLogger realLogger) {
        mRealLogger = realLogger;
        mLogger = new Logger(realLogger, getClass());
    }

    @Override
    public String getTestName() {
        return DEFAULT_TEST_NAME;
    }

    /** Gets a unique id for the test invocation. */
    public final int getTestInvocationId() {
        return mInvocationId;
    }

    // TODO(b/285014040): add more features like:
    // - sleep()
    // - logV()
    // - toString()
}
