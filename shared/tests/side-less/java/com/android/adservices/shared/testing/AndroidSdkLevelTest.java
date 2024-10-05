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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.AndroidSdk.Level.ANY;
import static com.android.adservices.shared.testing.AndroidSdk.Level.DEV;
import static com.android.adservices.shared.testing.AndroidSdk.Level.R;
import static com.android.adservices.shared.testing.AndroidSdk.Level.S;
import static com.android.adservices.shared.testing.AndroidSdk.Level.S2;
import static com.android.adservices.shared.testing.AndroidSdk.Level.T;
import static com.android.adservices.shared.testing.AndroidSdk.Level.U;
import static com.android.adservices.shared.testing.AndroidSdk.Level.V;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;
import com.android.adservices.shared.testing.AndroidSdk.Level;

import org.junit.Test;

public final class AndroidSdkLevelTest extends SharedSidelessTestCase {

    @Test
    public void testFactoryMethod() {
        Level forDev = Level.forLevel(10_000);
        expect.withMessage("level 10_000").that(forDev).isSameInstanceAs(DEV);

        Level forFutureVersion = Level.forLevel(108);
        expect.withMessage("level 108").that(forFutureVersion).isSameInstanceAs(DEV);

        Level for30 = Level.forLevel(30);
        expect.withMessage("level 30").that(for30).isSameInstanceAs(R);

        Level for31 = Level.forLevel(31);
        expect.withMessage("level 31").that(for31).isSameInstanceAs(S);

        Level for32 = Level.forLevel(32);
        expect.withMessage("level 32").that(for32).isSameInstanceAs(S2);

        Level for33 = Level.forLevel(33);
        expect.withMessage("level 33").that(for33).isSameInstanceAs(T);

        Level for34 = Level.forLevel(34);
        expect.withMessage("level 34").that(for34).isSameInstanceAs(U);

        Level for35 = Level.forLevel(35);
        expect.withMessage("level 35").that(for35).isSameInstanceAs(V);

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> Level.forLevel(29));
        expect.that(e).hasMessageThat().contains("29");
    }

    @Test
    public void testGetLevel() {
        expect.withMessage("level of ANY").that(ANY.getLevel()).isLessThan(R.getLevel());
        expect.withMessage("level of DEV").that(DEV.getLevel()).isEqualTo(10_000);
        expect.withMessage("level of R").that(R.getLevel()).isEqualTo(30);
        expect.withMessage("level of S").that(S.getLevel()).isEqualTo(31);
        expect.withMessage("level of S2").that(S2.getLevel()).isEqualTo(32);
        expect.withMessage("level of T").that(T.getLevel()).isEqualTo(33);
        expect.withMessage("level of U").that(U.getLevel()).isEqualTo(34);
        expect.withMessage("level of V").that(V.getLevel()).isEqualTo(35);
    }

    @Test
    public void testAtLeast() {
        expect.withMessage("ANY.isAtLeast(DEV)").that(ANY.isAtLeast(DEV)).isFalse();
        expect.withMessage("ANY.isAtLeast(ANY)").that(ANY.isAtLeast(ANY)).isTrue();
        expect.withMessage("ANY.isAtLeast(R)").that(ANY.isAtLeast(R)).isFalse();
        expect.withMessage("ANY.isAtLeast(S)").that(ANY.isAtLeast(S)).isFalse();
        expect.withMessage("ANY.isAtLeast(S2)").that(ANY.isAtLeast(S2)).isFalse();
        expect.withMessage("ANY.isAtLeast(T)").that(ANY.isAtLeast(T)).isFalse();
        expect.withMessage("ANY.isAtLeast(U)").that(ANY.isAtLeast(U)).isFalse();
        expect.withMessage("ANY.isAtLeast(V)").that(ANY.isAtLeast(V)).isFalse();

        expect.withMessage("DEV.isAtLeast(DEV)").that(DEV.isAtLeast(DEV)).isTrue();
        expect.withMessage("DEV.isAtLeast(ANY)").that(DEV.isAtLeast(ANY)).isTrue();
        expect.withMessage("DEV.isAtLeast(R)").that(DEV.isAtLeast(R)).isTrue();
        expect.withMessage("DEV.isAtLeast(S)").that(DEV.isAtLeast(S)).isTrue();
        expect.withMessage("DEV.isAtLeast(S2)").that(DEV.isAtLeast(S2)).isTrue();
        expect.withMessage("DEV.isAtLeast(T)").that(DEV.isAtLeast(T)).isTrue();
        expect.withMessage("DEV.isAtLeast(U)").that(DEV.isAtLeast(U)).isTrue();
        expect.withMessage("DEV.isAtLeast(V)").that(DEV.isAtLeast(V)).isTrue();

        expect.withMessage("S.isAtLeast(DEV)").that(S.isAtLeast(DEV)).isFalse();
        expect.withMessage("R.isAtLeast(ANY)").that(R.isAtLeast(ANY)).isTrue();
        expect.withMessage("R.isAtLeast(R)").that(R.isAtLeast(R)).isTrue();
        expect.withMessage("R.isAtLeast(S)").that(R.isAtLeast(S)).isFalse();
        expect.withMessage("R.isAtLeast(S2)").that(R.isAtLeast(S2)).isFalse();
        expect.withMessage("R.isAtLeast(T)").that(R.isAtLeast(T)).isFalse();
        expect.withMessage("R.isAtLeast(U)").that(R.isAtLeast(U)).isFalse();
        expect.withMessage("R.isAtLeast(V)").that(R.isAtLeast(V)).isFalse();

        expect.withMessage("S.isAtLeast(DEV)").that(S.isAtLeast(DEV)).isFalse();
        expect.withMessage("S.isAtLeast(ANY)").that(S.isAtLeast(ANY)).isTrue();
        expect.withMessage("S.isAtLeast(R)").that(S.isAtLeast(R)).isTrue();
        expect.withMessage("S.isAtLeast(S)").that(S.isAtLeast(S)).isTrue();
        expect.withMessage("S.isAtLeast(S2)").that(S.isAtLeast(S2)).isFalse();
        expect.withMessage("S.isAtLeast(T)").that(S.isAtLeast(T)).isFalse();
        expect.withMessage("S.isAtLeast(U)").that(S.isAtLeast(U)).isFalse();
        expect.withMessage("S.isAtLeast(V)").that(S.isAtLeast(V)).isFalse();

        expect.withMessage("T.isAtLeast(DEV)").that(T.isAtLeast(DEV)).isFalse();
        expect.withMessage("T.isAtLeast(ANY)").that(T.isAtLeast(ANY)).isTrue();
        expect.withMessage("T.isAtLeast(R)").that(T.isAtLeast(R)).isTrue();
        expect.withMessage("T.isAtLeast(S)").that(T.isAtLeast(S)).isTrue();
        expect.withMessage("T.isAtLeast(S2)").that(T.isAtLeast(S2)).isTrue();
        expect.withMessage("T.isAtLeast(T)").that(T.isAtLeast(T)).isTrue();
        expect.withMessage("T.isAtLeast(U)").that(T.isAtLeast(U)).isFalse();
        expect.withMessage("T.isAtLeast(V)").that(T.isAtLeast(V)).isFalse();

        expect.withMessage("U.isAtLeast(DEV)").that(U.isAtLeast(DEV)).isFalse();
        expect.withMessage("U.isAtLeast(ANY)").that(U.isAtLeast(ANY)).isTrue();
        expect.withMessage("U.isAtLeast(R)").that(U.isAtLeast(R)).isTrue();
        expect.withMessage("U.isAtLeast(S)").that(U.isAtLeast(S)).isTrue();
        expect.withMessage("U.isAtLeast(S2)").that(U.isAtLeast(S2)).isTrue();
        expect.withMessage("U.isAtLeast(T)").that(U.isAtLeast(T)).isTrue();
        expect.withMessage("U.isAtLeast(U)").that(U.isAtLeast(U)).isTrue();
        expect.withMessage("U.isAtLeast(V)").that(U.isAtLeast(V)).isFalse();

        expect.withMessage("V.isAtLeast(DEV)").that(V.isAtLeast(DEV)).isFalse();
        expect.withMessage("V.isAtLeast(ANY)").that(V.isAtLeast(ANY)).isTrue();
        expect.withMessage("V.isAtLeast(R)").that(V.isAtLeast(R)).isTrue();
        expect.withMessage("V.isAtLeast(S)").that(V.isAtLeast(S)).isTrue();
        expect.withMessage("V.isAtLeast(S2)").that(V.isAtLeast(S2)).isTrue();
        expect.withMessage("V.isAtLeast(T)").that(V.isAtLeast(T)).isTrue();
        expect.withMessage("V.isAtLeast(U)").that(V.isAtLeast(U)).isTrue();
        expect.withMessage("V.isAtLeast(V)").that(V.isAtLeast(V)).isTrue();
    }
}
