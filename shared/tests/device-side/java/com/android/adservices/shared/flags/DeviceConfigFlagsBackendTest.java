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

package com.android.adservices.shared.flags;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;

import android.provider.DeviceConfig;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Test;

public final class DeviceConfigFlagsBackendTest extends SharedExtendedMockitoTestCase {

    private final String mName = "Bond, James Bond";
    private final String mNamespace = "The Name is Space, Namespace";
    private final DeviceConfigFlagsBackend mBackend = new DeviceConfigFlagsBackend(mNamespace);

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new)
                .build();
    }

    @Test
    public void testNullConstructor() {
        assertThrows(NullPointerException.class, () -> new DeviceConfigFlagsBackend(null));
    }

    @Test
    public void testGetters_null() {
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, true));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, "Dude"));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, 42));
        assertThrows(NullPointerException.class, () -> mBackend.getFlag(null, 4815162342L));
    }

    @Test
    public void testGetFlag_default() {
        expect.withMessage("getFlag() before set").that(mBackend.getFlag(mName)).isNull();

        setDeviceConfigProperty("I Set, therefore I Am");
        expect.withMessage("getFlag() after set")
                .that(mBackend.getFlag(mName))
                .isEqualTo("I Set, therefore I Am");
    }

    @Test
    public void testGetFlag_boolean() {
        expect.withMessage("getFlag() before set").that(mBackend.getFlag(mName, true)).isTrue();

        setDeviceConfigProperty("true");
        expect.withMessage("getFlag() after set").that(mBackend.getFlag(mName, false)).isTrue();

        // Make sure it overrode getFlag(name, defaultValue) (instead of using getFlag(name))
        verify(() -> DeviceConfig.getBoolean(mNamespace, mName, true));
        verify(() -> DeviceConfig.getBoolean(mNamespace, mName, false));
    }

    @Test
    public void testGetFlag_string() {
        expect.withMessage("getFlag() before set")
                .that(mBackend.getFlag(mName, "Set...NOT!"))
                .isEqualTo("Set...NOT!");

        setDeviceConfigProperty("Set I am!");
        expect.withMessage("getFlag() after set")
                .that(mBackend.getFlag(mName, "Set...NOT!"))
                .isEqualTo("Set I am!");

        // Make sure it overrode getFlag(name, defaultValue) (instead of using getFlag(name))
        verify(() -> DeviceConfig.getString(mNamespace, mName, "Set...NOT!"), times(2));
    }

    @Test
    public void testGetFlag_int() {
        expect.withMessage("getFlag() before set")
                .that(mBackend.getFlag(mName, 108))
                .isEqualTo(108);

        setDeviceConfigProperty("42");
        expect.withMessage("getFlag() after set").that(mBackend.getFlag(mName, 108)).isEqualTo(42);

        // Make sure it overrode getFlag(name, defaultValue) (instead of using getFlag(name))
        verify(() -> DeviceConfig.getInt(mNamespace, mName, 108), times(2));
    }

    @Test
    public void testGetFlag_long() {
        expect.withMessage("getFlag() before set")
                .that(mBackend.getFlag(mName, 666L))
                .isEqualTo(666L);

        setDeviceConfigProperty("4815162342");
        expect.withMessage("getFlag() after set")
                .that(mBackend.getFlag(mName, 666L))
                .isEqualTo(4815162342L);

        // Make sure it overrode getFlag(name, defaultValue) (instead of using getFlag(name))
        verify(() -> DeviceConfig.getLong(mNamespace, mName, 666L), times(2));
    }

    @Test
    public void testGetFlag_float() {
        expect.withMessage("getFlag() before set")
                .that(mBackend.getFlag(mName, 1.08F))
                .isEqualTo(1.08F);

        setDeviceConfigProperty("4.2");
        expect.withMessage("getFlag() after set")
                .that(mBackend.getFlag(mName, 1.08F))
                .isEqualTo(4.2F);

        // Make sure it overrode getFlag(name, defaultValue) (instead of using getFlag(name))
        verify(() -> DeviceConfig.getFloat(mNamespace, mName, 1.08F), times(2));
    }

    private void setDeviceConfigProperty(String value) {
        mLog.v("setProperty(%s)", value);
        DeviceConfig.setProperty(mNamespace, mName, value, /* makeDefault= */ false);
    }
}
