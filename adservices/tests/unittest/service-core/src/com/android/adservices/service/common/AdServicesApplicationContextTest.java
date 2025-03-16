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

package com.android.adservices.service.common;

import static com.android.adservices.shared.testing.common.DumpHelper.dump;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class AdServicesApplicationContextTest extends AdServicesUnitTestCase {

    @Test
    public void testConstructor_null() {
        assertThrows(NullPointerException.class, () -> new AdServicesApplicationContext(null));
    }

    @Test
    public void testGetBaseContext() {
        var context = new AdServicesApplicationContext(mContext);

        expect.withMessage("getBaseContext()")
                .that(context.getBaseContext())
                .isSameInstanceAs(mContext);
    }

    @Test
    public void testDump() throws Exception {
        var context = new AdServicesApplicationContext(mContext);

        var dump = dump(pw -> context.dump(pw, /* args= */ null));

        expect.withMessage("dump()").that(dump).contains("user: " + context.getUser());
        expect.withMessage("dump()").that(dump).contains("baseContext: " + mContext);
        expect.withMessage("dump()")
                .that(dump)
                .contains("applicationContext: " + context.getApplicationContext());
        expect.withMessage("dump()").that(dump).contains("dataDir: " + context.getDataDir());
    }
}
