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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.AllowList.DEFAULT_DELIMITER;
import static com.android.adservices.service.common.AllowList.DEFAULT_SET_ALL_STRING;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.util.Set;

/** Unit test for {@link AllowList}. */
public final class AllowListTest extends AdServicesUnitTestCase {
    private static final String TEST_DELIMITER = "@";
    private static final String TEST_SET_ALL_STRING = "set_all";
    private static final String TEST_NAME_1 = "name_1";
    private static final String TEST_NAME_2 = "name_2";
    private static final String TEST_ALLOW_LIST_DEFAULT =
            TEST_NAME_1 + DEFAULT_DELIMITER + TEST_NAME_2;
    private static final String TEST_ALLOW_LIST = TEST_NAME_1 + TEST_DELIMITER + TEST_NAME_2;
    private static final String TEST_DENY_LIST_DEFAULT = TEST_ALLOW_LIST_DEFAULT;
    private static final String TEST_DENY_LIST = TEST_ALLOW_LIST;

    @Test
    public void testIsAllowed_onlyInAllowList() {
        AllowList allowList =
                new AllowList(
                        TEST_ALLOW_LIST, /* denyList= */ "", TEST_DELIMITER, TEST_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isTrue();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isTrue();

        // Check one element
        allowList =
                new AllowList(TEST_NAME_1, /* denyList= */ "", TEST_DELIMITER, TEST_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isTrue();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check set all.
        allowList =
                new AllowList(
                        TEST_SET_ALL_STRING,
                        /* denyList= */ "",
                        TEST_DELIMITER,
                        TEST_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isTrue();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isTrue();
    }

    @Test
    public void testIsAllowed_onlyInDenyList() {
        AllowList allowList =
                new AllowList(
                        /* allowList= */ "", TEST_DENY_LIST, TEST_DELIMITER, TEST_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check one element
        allowList =
                new AllowList(
                        /* allowList= */ "", TEST_NAME_1, TEST_DELIMITER, TEST_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check set all
        allowList =
                new AllowList(
                        /* allowList= */ "",
                        TEST_SET_ALL_STRING,
                        TEST_DELIMITER,
                        TEST_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();
    }

    @Test
    public void testIsAllowed_inBothAllowListAndDenyList() {
        AllowList allowList =
                new AllowList(TEST_ALLOW_LIST, TEST_DENY_LIST, TEST_DELIMITER, TEST_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check one element
        allowList = new AllowList(TEST_NAME_1, TEST_DENY_LIST, TEST_DELIMITER, TEST_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        allowList =
                new AllowList(TEST_ALLOW_LIST, TEST_NAME_1, TEST_DELIMITER, TEST_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isTrue();

        // Check set all
        allowList =
                new AllowList(
                        TEST_SET_ALL_STRING, TEST_DENY_LIST, TEST_DELIMITER, TEST_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        allowList =
                new AllowList(
                        TEST_ALLOW_LIST, TEST_SET_ALL_STRING, TEST_DELIMITER, TEST_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        allowList =
                new AllowList(
                        TEST_SET_ALL_STRING,
                        TEST_SET_ALL_STRING,
                        TEST_DELIMITER,
                        TEST_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();
    }

    @Test
    public void testIsAllowed_onlyInAllowList_defaultDelimiterAndSetAll() {
        AllowList allowList = new AllowList(TEST_ALLOW_LIST_DEFAULT, /* denyList= */ "");

        expect.that(allowList.isAllowed(TEST_NAME_1)).isTrue();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isTrue();

        // Check one element
        allowList = new AllowList(TEST_NAME_1, /* denyList= */ "");
        expect.that(allowList.isAllowed(TEST_NAME_1)).isTrue();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check set all.
        allowList = new AllowList(DEFAULT_SET_ALL_STRING, /* denyList= */ "");

        expect.that(allowList.isAllowed(TEST_NAME_1)).isTrue();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isTrue();
    }

    @Test
    public void testIsAllowed_onlyInDenyList_defaultDelimiterAndSetAll() {
        AllowList allowList = new AllowList(/* allowList= */ "", TEST_DENY_LIST_DEFAULT);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check one element
        allowList = new AllowList(/* allowList= */ "", TEST_NAME_1);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check set all
        allowList = new AllowList(/* allowList= */ "", DEFAULT_SET_ALL_STRING);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();
    }

    @Test
    public void testIsAllowed_inBothAllowListAndDenyList_defaultDelimiterAndSetAll() {
        AllowList allowList = new AllowList(TEST_ALLOW_LIST_DEFAULT, TEST_DENY_LIST_DEFAULT);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        // Check one element
        allowList = new AllowList(TEST_NAME_1, TEST_DENY_LIST_DEFAULT);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        allowList = new AllowList(TEST_ALLOW_LIST_DEFAULT, TEST_NAME_1);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isTrue();

        // Check set all
        allowList = new AllowList(DEFAULT_SET_ALL_STRING, TEST_DENY_LIST_DEFAULT);

        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        allowList = new AllowList(TEST_ALLOW_LIST_DEFAULT, DEFAULT_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();

        allowList = new AllowList(DEFAULT_SET_ALL_STRING, DEFAULT_SET_ALL_STRING);
        expect.that(allowList.isAllowed(TEST_NAME_1)).isFalse();
        expect.that(allowList.isAllowed(TEST_NAME_2)).isFalse();
    }

    @Test
    public void testNullParamInConstructor() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AllowList(
                                /* allowList= */ "",
                                /* denyList= */ "",
                                /* delimiter= */ null,
                                DEFAULT_SET_ALL_STRING));
        assertThrows(
                NullPointerException.class,
                () ->
                        new AllowList(
                                /* allowList= */ "",
                                /* denyList= */ "",
                                DEFAULT_DELIMITER,
                                /* setAllString= */ null));
    }

    @Test
    public void testGetters() {
        AllowList allowList = new AllowList(TEST_ALLOW_LIST_DEFAULT, TEST_DENY_LIST_DEFAULT);

        expect.that(allowList.isAllowAll()).isFalse();
        expect.that(allowList.isDenyAll()).isFalse();
        expect.that(allowList.getAllowSet()).isEqualTo(Set.of(TEST_NAME_1, TEST_NAME_2));
        expect.that(allowList.getDenySet()).isEqualTo(Set.of(TEST_NAME_1, TEST_NAME_2));

        allowList = new AllowList(DEFAULT_SET_ALL_STRING, DEFAULT_SET_ALL_STRING);

        expect.that(allowList.isAllowAll()).isTrue();
        expect.that(allowList.isDenyAll()).isTrue();
        expect.that(allowList.getAllowSet()).isEqualTo(Set.of(DEFAULT_SET_ALL_STRING));
        expect.that(allowList.getDenySet()).isEqualTo(Set.of(DEFAULT_SET_ALL_STRING));
    }
}
