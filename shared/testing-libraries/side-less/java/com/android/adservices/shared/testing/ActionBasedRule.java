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

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base rule used to execute actions before the test (and revert them afterwards).
 *
 * @param <R> concrete implementation of the rule
 */
public abstract class ActionBasedRule<R extends ActionBasedRule<R>> extends AbstractRule {

    private final List<Action> mActions = new ArrayList<>();
    private final List<Action> mActionsToBeReverted = new ArrayList<>();
    private boolean mIsRunning;

    protected ActionBasedRule(RealLogger logger) {
        super(logger);
    }

    /**
     * Adds an action to be executed after the test starts (and reverted after it ends).
     *
     * <p>This method is mostly useful to add actions based on annotations present in the test.
     *
     * @return self
     * @throws IllegalStateException if the test already started.
     */
    protected R addAction(Action action) {
        Objects.requireNonNull(action, "action cannot be null");

        if (mIsRunning) {
            throw new IllegalStateException(
                    "Cannot add action " + action + " because test is already running");
        }

        cacheAction(action);
        return getSelf();
    }

    private void cacheAction(Action action) {
        if (mActions.contains(action)) {
            // NOTE: in theory it should be fine to add duplicated actions, as it would be up to the
            // action itself to throw if executed twice. But it's probably better to fail earlier...
            throw new IllegalArgumentException("action already added: " + action);
        }
        mLog.d("Caching %s as test is not running yet", action);
        mActions.add(action);
    }

    /**
     * Executes the action right away, or cache it to be executed after the test starts.
     *
     * <p>This method is mostly useful to implement methods that the rule clients can call (like
     * {@code setFlag()}).
     *
     * <p>Notice that if it's executed right away it won't be reverted at the end of the test, but
     * if it's cached it will.
     *
     * @return self
     * @throws Exception propagated from {@code action}
     */
    protected R executeOrCache(Action action) throws Exception {
        Objects.requireNonNull(action, "action cannot be null");

        if (!mIsRunning) {
            cacheAction(action);
        } else {
            mLog.v(
                    "executeOrCache(%s): executing right way as test (%s) is running",
                    action, getTestName());
            action.execute();
        }

        return getSelf();
    }

    /**
     * Hook to let subclass perform additional checks before the actions are executed.
     *
     * <p>Do nothing by default
     */
    protected void preExecuteActions(Statement base, Description description) throws Throwable {}

    @Override
    protected final void evaluate(Statement base, Description description) throws Throwable {
        resetActions();
        // TODO(b/297085722): preExecuteActions() is currently used to let subclasses scan
        // annotations, but in reality we'll need a custom method for that specifically purpose,
        // as the rule could be used as a static class rule and then annotations from each test
        // would be added, but not removed...
        preExecuteActions(base, description);
        executeActions();
        mIsRunning = true;
        try {
            base.evaluate();
        } finally {
            mIsRunning = false;
            revertActions();
        }
    }

    /** Hook to let subclasses add their info to the string returned by {@link #toString()}. */
    protected void decorateToString(StringBuilder string) {}

    @Override
    public final String toString() {
        StringBuilder string =
                new StringBuilder(getClass().getSimpleName().toString())
                        .append("[mRunning=")
                        .append(mIsRunning);
        decorateToString(string);
        return string.append(']').toString();
    }

    private void resetActions() throws Throwable {
        mLog.d("resetActions(): resetting %d actions", mActions.size());
        mActions.forEach(Action::reset);
    }

    private void executeActions() throws Throwable {
        int size = mActions.size();
        mLog.i("executeActions(): executing %d actions", size);
        Action action = null;
        int i = 0;
        try {
            for (; i < size; i++) {
                action = mActions.get(i);
                mLog.d("Executing %s", action);
                boolean revert = action.execute();
                if (revert) {
                    mLog.v("Adding %s for reversion", action);
                    mActionsToBeReverted.add(action);
                } else {
                    mLog.d("%s won't be reverted", action);
                }
            }
        } catch (Throwable t) {
            // TODO(b/328682831): for now it's only logging, but it should throw using the
            // TestFailure exception
            mLog.e(t, "Failed to execute action#%d (%s)", i, action);
            revertActions();
            throw t;
        }
    }

    private void revertActions() throws Exception {
        int size = mActionsToBeReverted.size();
        mLog.i("revertActions(): reverting %d actions (out of %d total)", size, mActions.size());
        for (int i = size - 1; i >= 0; i--) {
            Action action = mActionsToBeReverted.get(i);
            try {
                action.revert();
            } catch (Throwable t) {
                // TODO(b/328682831): for now it's only logging, but it should throw using the
                // TestFailure exception
                mLog.w(t, "Failed to revert action (%s), but not failing test", action);
            }
        }
    }

    /** Returns a cast reference to this rule */
    @SuppressWarnings("unchecked")
    protected R getSelf() {
        return (R) this;
    }
}
