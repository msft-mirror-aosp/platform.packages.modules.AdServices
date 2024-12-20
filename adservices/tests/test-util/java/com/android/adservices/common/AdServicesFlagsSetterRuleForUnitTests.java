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

package com.android.adservices.common;

import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_DATABASE_SCHEMA_VERSION_8;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_LOGGED_TOPIC;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_EXTENDED_METRICS_ENABLED;

import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.service.FakeFlagsFactory.SetDefaultFledgeFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.NameValuePair;

import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Base class of {@code FlagsSetterRule} to be used by unit tests.
 *
 * <p>It won't use {@code DeviceConfig} directly, but rather stub it (using mocks or fake flags).
 *
 * @param <R> concrete rule implementation
 * @param <F> type of flags implementation used by the rule.
 */
public abstract class AdServicesFlagsSetterRuleForUnitTests<
                R extends AdServicesFlagsSetterRuleForUnitTests<R>>
        extends AbstractAdServicesFlagsSetterRule<R> {

    protected final Flags mFlags;

    protected AdServicesFlagsSetterRuleForUnitTests(
            Flags flags, Consumer<NameValuePair> flagsSetter) {
        super(AndroidLogger.getInstance(), flagsSetter);
        mFlags = Objects.requireNonNull(flags, "flags cannot be null");
        mLog.d("Constructed for %s", flags);
    }

    /**
     * Gets the flags implementation.
     *
     * <p>Typically used by test classes to pass to the object under test.
     */
    public final Flags getFlags() {
        return mFlags;
    }

    // NOTE: currently is only used by unit tests, but it might be worth to move to superclass so
    // it can be used by CTS tests as well
    /**
     * Sets the default flags used by {@code FLEDGE} tests.
     *
     * <p>In other words, the same flags from {@code FakeFlagsFactory.TestFlags}.
     */
    public final R setDefaultFledgeFlags() {
        mLog.i("setDefaultFledgeFlags()");
        setDefaultFledgeFlags((name, value) -> setNameValuePair(name, value));
        return getThis();
    }

    static void setDefaultFledgeFlags(BiConsumer<String, String> nameValueSetter) {
        nameValueSetter.accept(KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS, "10000");
        nameValueSetter.accept(KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS, "10000");
        nameValueSetter.accept(KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS, "600000");
        nameValueSetter.accept(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, "true");
        nameValueSetter.accept(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED, "true");
        nameValueSetter.accept(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, "true");
        nameValueSetter.accept(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED, "true");
        nameValueSetter.accept(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE, "-100");
        nameValueSetter.accept(KEY_ENABLE_LOGGED_TOPIC, "true");
        nameValueSetter.accept(KEY_ENABLE_DATABASE_SCHEMA_VERSION_8, "true");
        nameValueSetter.accept(KEY_FLEDGE_AUCTION_SERVER_ENABLED, "true");
        nameValueSetter.accept(KEY_FLEDGE_EVENT_LEVEL_DEBUG_REPORTING_ENABLED, "true");
        nameValueSetter.accept(KEY_FLEDGE_BEACON_REPORTING_METRICS_ENABLED, "true");
        nameValueSetter.accept(KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED, "true");
        nameValueSetter.accept(KEY_FLEDGE_AUCTION_SERVER_KEY_FETCH_METRICS_ENABLED, "true");
        nameValueSetter.accept(KEY_PAS_EXTENDED_METRICS_ENABLED, "true");
    }

    @Override
    protected final boolean isAnnotationSupported(Annotation annotation) {
        return (annotation instanceof SetDefaultFledgeFlags)
                || super.isAnnotationSupported(annotation);
    }

    @Override
    protected final void processAnnotation(Description description, Annotation annotation) {
        // NOTE: add annotations sorted by "most likely usage"
        if (annotation instanceof SetDefaultFledgeFlags) {
            setDefaultFledgeFlags();
        } else {
            super.processAnnotation(description, annotation);
        }
    }

    @Override
    protected final String getTestPackageName() {
        return InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
    }

    @Override
    protected final int getDeviceSdk() {
        return Build.VERSION.SDK_INT;
    }

    // NOTE: in theory we could add an annotation to set it as well, but it would be an overkill as
    // it's supposed to be a temporary measure as ideally tests should explicitly set what they need
    /**
     * Sets the behavior for {@code getFlag(name, defaultValue)} when the test did not explicitly
     * set the value of the flag.
     */
    public abstract R setMissingFlagBehavior(MissingFlagBehavior behavior);

    private void setNameValuePair(String name, String value) {
        mLog.v("setNameValuePair(%s, %s)", name, value);
        setFlag(new NameValuePair(name, value));
    }
}
