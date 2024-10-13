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

    protected ActionBasedRule(RealLogger logger) {
        super(logger);
    }

    /** Adds an action. */
    protected R addAction(Action action) {
        Objects.requireNonNull(action, "action cannot be null");

        if (mActions.contains(action)) {
            // NOTE: in theory it should be fine to add duplicated actions, as it would be up to the
            // action itself to throw if executed twice. But it's probably better to fail earlier...
            throw new IllegalArgumentException("action already added: " + action);
        }

        mActions.add(action);
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
        preExecuteActions(base, description);
        executeActions();
        try {
            base.evaluate();
        } finally {
            revertActions();
        }
    }

    private void executeActions() throws Throwable {
        Action action = null;
        int i = 0;
        try {
            for (; i < mActions.size(); i++) {
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
        for (Action action : mActionsToBeReverted) {
            try {
                action.revert();
            } catch (Throwable t) {
                // TODO(b/328682831): for now it's only logging, but it should throw using the
                // TestFailure exception
                mLog.w(t, "Failed to revert action (%s), but not failing test", action);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private R getSelf() {
        return (R) this;
    }
}
