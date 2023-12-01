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
package com.android.adservices.shared.testing.common;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.shared.common.ApplicationContextSingleton;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;

/**
 * Rule used to properly set / reset the {@link ApplicationContextSingleton} for tests - it will set
 * it before the test, and reset to the previous value afterwards.
 *
 * <p>It also provide convenience methods to {@link #get() get} / {@link #set(Context) set} the
 * context during tests, so the test doesn't need to explicitly deal with {@link
 * ApplicationContextSingleton}.
 */
public final class ApplicationContextSingletonRule implements TestRule {

    public static final String TAG = ApplicationContextSingletonRule.class.getSimpleName();

    private final Context mContext;

    /** Default constructor, sets the singleton as the target context of the instrumented app. */
    public ApplicationContextSingletonRule() {
        this(ApplicationProvider.getApplicationContext());
    }

    /** Sets the singleton using the given {@code context}. */
    public ApplicationContextSingletonRule(Context context) {
        mContext = Objects.requireNonNull(context, "context cannot be null");
        Log.v(TAG, "Constructing with " + context);
    }

    /** Convenience method to get the current {@link ApplicationContextSingleton}. */
    public Context get() {
        Context context = ApplicationContextSingleton.getForTests();
        Log.v(TAG, "get(): " + context);
        return context;
    }

    /** Convenience method to set the {@link ApplicationContextSingleton}. */
    public void set(Context context) {
        Log.v(TAG, "set(" + context + ")");
        ApplicationContextSingleton.setForTests(context);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Context previousContext = ApplicationContextSingleton.getForTests();
                Log.d(
                        TAG,
                        "Changing ApplicationContextSingleton from "
                                + previousContext
                                + " to "
                                + mContext);
                ApplicationContextSingleton.setForTests(mContext);
                try {
                    base.evaluate();
                } finally {
                    Log.d(TAG, "Restoring ApplicationContextSingleton to " + previousContext);
                    ApplicationContextSingleton.setForTests(previousContext);
                }
            }
        };
    }
}
