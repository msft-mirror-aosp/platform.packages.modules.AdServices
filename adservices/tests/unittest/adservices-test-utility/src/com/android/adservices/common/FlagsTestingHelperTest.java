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

import static com.android.adservices.common.FlagsTestingHelper.asMap;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.Test;

public final class FlagsTestingHelperTest extends AdServicesMockitoTestCase {

    @Test
    public void testAsMap_null() {
        assertThrows(NullPointerException.class, () -> asMap(null));
    }

    @Test
    public void testAsMap() {
        when(mMockFlags.getAdIdCacheTtlMs()).thenReturn(4815162342L);
        when(mMockFlags.getMddPackageDenyRegistryManifestFileUrl()).thenReturn(null);

        var map = asMap(mMockFlags);

        assertWithMessage("asMap()").that(map).isNotNull();

        expect.withMessage("asMap()").that(map).containsEntry("getAdIdCacheTtlMs()", "4815162342");
        expect.withMessage("asMap()")
                .that(map)
                .containsEntry("getMddPackageDenyRegistryManifestFileUrl()", "null");
        // isEnrollmentBlocklisted(...) takes args, so it should be ignored
        expect.withMessage("asMap()").that(map).doesNotContainKey("isEnrollmentBlocklisted");
    }

    @Test
    public void testAsMap_fails() {
        RuntimeException e = new RuntimeException("D'OH!");
        when(mMockFlags.getAdIdCacheTtlMs()).thenThrow(e);

        var actual = assertThrows(RuntimeException.class, () -> asMap(mMockFlags));

        expect.withMessage("thrown exception").that(actual).isSameInstanceAs(e);
    }

    @Test
    public void testAsMapWithPrefix_null() {
        assertThrows(NullPointerException.class, () -> asMap(mMockFlags, /* prefix= */ null));
        assertThrows(
                NullPointerException.class, () -> asMap(/* flags= */ null, /* prefix= */ "00"));
    }

    @Test
    public void testAsMapWithPrefix() {
        when(mMockFlags.getAdIdCacheTtlMs()).thenReturn(4815162342L);
        when(mMockFlags.getMddPackageDenyRegistryManifestFileUrl()).thenReturn(null);
        when(mMockFlags.getMddCobaltRegistryManifestFileUrl()).thenReturn("/dev/null");

        var map = asMap(mMockFlags, "getMdd");

        assertWithMessage("asMap()").that(map).isNotNull();

        expect.withMessage("asMap()").that(map).doesNotContainKey("getAdIdCacheTtlMs()");
        expect.withMessage("asMap()")
                .that(map)
                .containsEntry("getMddPackageDenyRegistryManifestFileUrl()", "null");
        expect.withMessage("asMap()")
                .that(map)
                .containsEntry("getMddCobaltRegistryManifestFileUrl()", "/dev/null");
    }
}
