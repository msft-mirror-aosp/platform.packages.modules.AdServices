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
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Uber" superclass for all tests.
 *
 * <p>It provide the bare minimum features that will be used by all sort of tests (unit / CTS,
 * device/host side, project-specific).
 */
public abstract class SidelessTestCase implements TestNamer {

    private static final boolean FAIL_ON_PROHIBITED_FIELDS = true;

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
        assertTestClassHasNoFieldsFromSuperclass(
                SidelessTestCase.class, "mLog", "mRealLogger", "expect");
    }

    /**
     * Asserts that the test class doesn't declare any field with the given names.
     *
     * <p>"Base" superclasses should use this method to passing all protected and public fields they
     * define.
     */
    public final void assertTestClassHasNoSuchField(String name, String reason) throws Exception {
        Objects.requireNonNull(name, "name cannot be nul");
        Objects.requireNonNull(reason, "reason cannot be nul");

        if (!iHaveThisField(name)) {
            return;
        }
        if (!FAIL_ON_PROHIBITED_FIELDS) {
            mLog.e(
                    "Class %s should not define field %s (reason: %s) but test is not failing"
                            + " because FAIL_ON_PROHIBITED_FIELDS is false",
                    getClass().getSimpleName(), name, reason);
            return;
        }
        expect.withMessage(
                        "Class %s should not define field %s. Reason: %s",
                        getClass().getSimpleName(), name, reason)
                .fail();
    }

    /**
     * Asserts that the test class doesn't declare any field with the given names.
     *
     * <p>"Base" superclasses should use this method to passing all protected and public fields they
     * define.
     */
    public final void assertTestClassHasNoFieldsFromSuperclass(Class<?> superclass, String... names)
            throws Exception {
        Objects.requireNonNull(superclass, "superclass cannot be null");
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("names cannot be empty or null");
        }
        Class<?> myClass = getClass();
        String myClassName = myClass.getSimpleName();
        if (myClass.equals(superclass)) {
            mLog.v(
                    "assertTestClassHasNoFieldsFromSuperclass(%s, %s): skipping on self",
                    myClassName, Arrays.toString(names));
            return;
        }

        StringBuilder violationsBuilder = new StringBuilder();
            for (String name : names) {
            if (iHaveThisField(name)) {
                    violationsBuilder.append(' ').append(name);
                }
            }
        String violations = violationsBuilder.toString();
        if (violations.isEmpty()) {
            return;
        }
        if (!FAIL_ON_PROHIBITED_FIELDS) {
            mLog.e(
                    "Class %s should not define the following fields (as they're defined by %s),"
                        + " but test is not failing because FAIL_ON_PROHIBITED_FIELDS is false:%s",
                    getClass().getSimpleName(), superclass.getSimpleName(), violations);
            return;
        }
        expect.withMessage(
                        "%s should not define the following fields, as they're defined by %s:%s",
                        myClassName, superclass.getSimpleName(), violations)
                .fail();
    }

    @Nullable
    private boolean iHaveThisField(String name) {
        try {
            Field field = getClass().getDeclaredField(name);
            // Logging as error as class is not expected to have it
            mLog.e(
                    "Found field with name (%s) that shouldn't exist on class %s: %s",
                    name, getClass().getSimpleName(), field);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    // TODO(b/285014040): add more features like:
    // - sleep()
    // - logV()
    // - toString()
}
