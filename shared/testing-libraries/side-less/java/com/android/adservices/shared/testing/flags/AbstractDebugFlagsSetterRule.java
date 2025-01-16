/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.adservices.shared.testing.flags;

import com.android.adservices.shared.testing.Action;
import com.android.adservices.shared.testing.ActionBasedRule;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairAction;
import com.android.adservices.shared.testing.NameValuePairSetter;
import com.android.adservices.shared.testing.TestHelper;
import com.android.adservices.shared.testing.annotations.DisableDebugFlag;
import com.android.adservices.shared.testing.annotations.DisableDebugFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.EnableDebugFlags;

import com.google.common.collect.ImmutableList;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A rule used to set {@code DebugFlags}.
 *
 * <p>Notice that we don't have an interface representing {@code DebugFlags} (yet?), just
 * annotations, so it's up to the subclasses to provide additional support for it.
 *
 * @param <R> concrete rule class
 */
public abstract class AbstractDebugFlagsSetterRule<R extends AbstractDebugFlagsSetterRule<R>>
        extends ActionBasedRule<R> {

    private final NameValuePairSetter mSetter;

    protected AbstractDebugFlagsSetterRule(RealLogger logger, NameValuePairSetter setter) {
        super(logger);
        mSetter = Objects.requireNonNull(setter, "setter cannot be null");
    }

    @Override
    protected final ImmutableList<Action> createActionsForTest(
            Statement base, Description description) throws Throwable {
        Map<String, Action> actions = new LinkedHashMap<>();
        var annotations =
                TestHelper.getAnnotationsFromEverywhere(
                        description,
                        annotation ->
                                (annotation instanceof EnableDebugFlag
                                        || annotation instanceof EnableDebugFlags
                                        || annotation instanceof DisableDebugFlag
                                        || annotation instanceof DisableDebugFlags));
        annotations.forEach(
                annotation -> {
                    if (annotation instanceof EnableDebugFlag) {
                        add(actions, fromEnableDebugFlag(annotation));
                    } else if (annotation instanceof DisableDebugFlag) {
                        add(actions, fromDisableDebugFlag(annotation));
                    }
                    if (annotation instanceof EnableDebugFlags) {
                        Arrays.stream(((EnableDebugFlags) annotation).value())
                                .forEach(a -> add(actions, fromEnableDebugFlag(a)));
                    }
                    if (annotation instanceof DisableDebugFlags) {
                        Arrays.stream(((DisableDebugFlags) annotation).value())
                                .forEach(a -> add(actions, fromDisableDebugFlag(a)));
                    }
                });
        mLog.v(
                "createActionsForTest(%s): returning %s from %s",
                getTestName(), actions, annotations);
        return ImmutableList.copyOf(actions.values());
    }

    private void add(Map<String, Action> actions, NameValuePairAction action) {
        String name = action.getNvp().name;
        if (actions.containsKey(name)) {
            mLog.v("Ignoring annotation already processed (%s)", action);
            return;
        }
        actions.put(name, action);
    }

    private NameValuePairAction fromEnableDebugFlag(Annotation annotation) {
        return newAction(((EnableDebugFlag) annotation).value(), true);
    }

    private NameValuePairAction fromDisableDebugFlag(Annotation annotation) {
        return newAction(((DisableDebugFlag) annotation).value(), false);
    }

    private NameValuePairAction newAction(String name, boolean value) {
        return new NameValuePairAction(
                mLog, mSetter, new NameValuePair(name, Boolean.toString(value)));
    }
}
