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

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Uber" superclass for all tests.
 *
 * <p>It provide the bare minimum features that will be used by all sort of tests (unit / CTS,
 * device/host side, project-specific).
 */
public abstract class SidelessTestCase implements TestNamer {

    // TODO(b/358120731): temporary false to avoid breaking the build as we need to fix existing
    // occurrences first. Once fixed, set to true;
    private static final boolean FAIL_ON_PROHIBITED_FIELDS = false;

    private static final AtomicInteger sNextInvocationId = new AtomicInteger();

    // TODO(b/342639109): set order
    @Rule public final Expect expect = Expect.create();

    private final int mInvocationId = sNextInvocationId.incrementAndGet();

    // TODO(b/285014040): log test number / to String on constructor (will require logV()).
    // Something like (which used to be on AdServicesTestCase):
    // Log.d(TAG, "setTestNumber(): " + getTestName() + " is test #" + mTestNumber);

    protected final Logger mLog;
    protected final RealLogger mRealLogger;

    public SidelessTestCase() {
        this(DynamicLogger.getInstance());
    }

    public SidelessTestCase(RealLogger realLogger) {
        mRealLogger = realLogger;
        mLog = new Logger(realLogger, getClass());
    }

    @Override
    public String getTestName() {
        return DEFAULT_TEST_NAME;
    }

    /** Gets a unique id for the test invocation. */
    public final int getTestInvocationId() {
        return mInvocationId;
    }

    @Test
    public final void testSidelessTestCaseFixtures() throws Exception {
        checkProhibitedFields("mLog", "mRealLogger", "expect");
    }

    /**
     * Asserts that the test class doesn't declare any field with the given names.
     *
     * <p>"Base" superclasses should use this method to passing all protected and public fields they
     * define.
     */
    protected final void checkProhibitedFields(String... names) throws Exception {
        checkProhibitedFields(/* isStatic= */ false, names);
    }

    /**
     * Asserts that the test class doesn't declare any static field with the given names.
     *
     * <p>"Base" superclasses should use this method to passing all protected and public static
     * fields they define.
     */
    protected final void checkProhibitedStaticFields(String... names) throws Exception {
        checkProhibitedFields(/* isStatic= */ true, names);
    }

    private void checkProhibitedFields(boolean isStatic, String... names) throws Exception {
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("names cannot be empty or null");
        }
        Class<?> myClass = getClass();
        String myClassName = myClass.getSimpleName();

        StringBuilder violationsBuilder = new StringBuilder();
        try {
            for (String name : names) {
                Object from = isStatic ? null : this;
                Object field = getField(myClass, from, name);
                if (field != null) {
                    violationsBuilder.append(' ').append(name);
                }
            }
        } catch (Exception | Error e) {
            if (FAIL_ON_PROHIBITED_FIELDS) {
                throw e;
            }
            throw new AssumptionViolatedException(
                    "checkProhibitedFields() failed, but ignoring:" + e);
        }
        String violations = violationsBuilder.toString();
        if (violations.isEmpty()) {
            return;
        }
        if (FAIL_ON_PROHIBITED_FIELDS) {
            throw new AssertionError(
                    myClassName + " should not define the following fields:" + violations);
        }
        throw new AssumptionViolatedException(
                myClassName
                        + " should not define the following fields, but ignoring the failure:"
                        + violations);
    }

    @Nullable
    private static Object getField(Class<?> clazz, Object object, String name) throws Exception {
        Field field = null;
        try {
            field = clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
        boolean before = field.isAccessible();
        try {
            field.setAccessible(true);
            return field.get(object);
        } finally {
            field.setAccessible(before);
        }
    }

    // TODO(b/285014040): add more features like:
    // - sleep()
    // - logV()
    // - toString()
}
