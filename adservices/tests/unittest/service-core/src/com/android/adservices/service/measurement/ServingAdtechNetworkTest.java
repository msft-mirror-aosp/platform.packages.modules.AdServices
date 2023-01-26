/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Set;

/** Unit tests for {@link ServingAdtechNetwork} */
@SmallTest
public final class ServingAdtechNetworkTest {

    @Test
    public void testCreation() throws Exception {
        ServingAdtechNetwork servingAdtechNetwork = createExample();
        assertEquals(12L, servingAdtechNetwork.getOffset().longValue());
    }

    @Test
    public void testDefaults() throws Exception {
        ServingAdtechNetwork servingAdtechNetwork = new ServingAdtechNetwork.Builder().build();
        assertNull(servingAdtechNetwork.getOffset());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final ServingAdtechNetwork network1 = createExample();
        final ServingAdtechNetwork network2 = createExample();
        final Set<ServingAdtechNetwork> networkSet1 = Set.of(network1);
        final Set<ServingAdtechNetwork> networkSet2 = Set.of(network2);
        assertEquals(network1.hashCode(), network2.hashCode());
        assertEquals(network1, network2);
        assertEquals(networkSet1, networkSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final ServingAdtechNetwork network1 = createExample();
        final ServingAdtechNetwork network2 =
                new ServingAdtechNetwork.Builder().setOffset(13L).build();
        final Set<ServingAdtechNetwork> networkSet1 = Set.of(network1);
        final Set<ServingAdtechNetwork> networkSet2 = Set.of(network2);
        assertNotEquals(network1.hashCode(), network2.hashCode());
        assertNotEquals(network1, network2);
        assertNotEquals(networkSet1, networkSet2);
    }

    private ServingAdtechNetwork createExample() {
        return new ServingAdtechNetwork.Builder().setOffset(12L).build();
    }
}
