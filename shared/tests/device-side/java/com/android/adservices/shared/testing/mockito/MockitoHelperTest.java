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
package com.android.adservices.shared.testing.mockito;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.SidelessSharedMockitoTestCase;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings("DirectInvocationOnMock") // is testing mocking stuff
public final class MockitoHelperTest extends SidelessSharedMockitoTestCase {

    // Used to get a "real" InvocationOnMock
    private final VoidAnswer mAnswer = new VoidAnswer();

    @Mock private Fixture mFixture;

    @Mock private InvocationOnMock mMockInvocation;

    @Test
    public void testToString_null() {
        assertThrows(NullPointerException.class, () -> MockitoHelper.toString(null));
    }

    @Test
    public void testToString_mockInvocation() {
        String toString = MockitoHelper.toString(mMockInvocation);

        expect.withMessage("MockitoHelper.toString(%s)", mMockInvocation)
                .that(toString)
                .isEqualTo("mMockInvocation");
    }

    @Test
    public void testToString_oneArgument() {
        doAnswer(mAnswer).when(mFixture).pEquals(any());
        mFixture.pEquals("NP");

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("pEquals(NP)");
    }

    @Test
    public void testToString_oneArgument_null() {
        doAnswer(mAnswer).when(mFixture).pEquals(any());
        mFixture.pEquals(null);

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("pEquals(null)");
    }

    @Test
    public void testToString_multipleArguments() {
        doAnswer(mAnswer).when(mFixture).toInfinity(any(), any());
        mFixture.toInfinity("and", "beyond");

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("toInfinity(and, beyond)");
    }

    @Test
    public void testToString_multipleArguments_null() {
        doAnswer(mAnswer).when(mFixture).toInfinity(any(), any());
        mFixture.toInfinity("and", null);

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("toInfinity(and, null)");
    }

    @Test
    public void testToString_varArgs() {
        doAnswer(mAnswer).when(mFixture).iJustCalled(any());
        mFixture.iJustCalled("", "to", "say", ",", "I", "love", "you", "!");

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("iJustCalled(, to, say, ,, I, love, you, !)");
    }

    @Test
    public void testToString_nullVarArgs() {
        doAnswer(mAnswer).when(mFixture).iJustCalled(any());
        mFixture.iJustCalled((Object[]) null);

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("iJustCalled(null)");
    }

    @Test
    public void testToString_varArgsWithNull() {
        doAnswer(mAnswer).when(mFixture).iJustCalled(any());
        mFixture.iJustCalled("", "to", "say", ",", "I", null, "you", "!");

        String toString = MockitoHelper.toString(mAnswer.invocation);

        expect.withMessage("MockitoHelper.toString(%s)", mAnswer.invocation)
                .that(toString)
                .isEqualTo("iJustCalled(, to, say, ,, I, null, you, !)");
    }

    @Test
    public void testIsMock() {
        Object mock = mock(Object.class);
        expect.withMessage("isMock(%s)", mock).that(MockitoHelper.isMock(mock)).isTrue();

        Object nonMock = new Object();
        expect.withMessage("isMock(%s)", nonMock).that(MockitoHelper.isMock(nonMock)).isFalse();
    }

    @Test
    public void testIsSpy() {
        Object mock = mock(Object.class);
        expect.withMessage("isSpy(%s)", mock).that(MockitoHelper.isSpy(mock)).isFalse();

        Object spied = new Object();
        expect.withMessage("isSpy(%s)", spied).that(MockitoHelper.isSpy(spied)).isFalse();

        Object spy = spy(spied);
        expect.withMessage("isMock(%s)", spy).that(MockitoHelper.isMock(spy)).isTrue();
        expect.withMessage("isSpy(%s)", spy).that(MockitoHelper.isMock(spy)).isTrue();
    }

    @Test
    public void testGetSpiedInstance() {
        Object mock = mock(Object.class);
        assertThrows(IllegalArgumentException.class, () -> MockitoHelper.getSpiedInstance(mock));

        Object spied = new Object();
        assertThrows(IllegalArgumentException.class, () -> MockitoHelper.getSpiedInstance(spied));

        Object spy = spy(spied);
        expect.withMessage("getSpiedInstance(%s)", spy)
                .that(MockitoHelper.getSpiedInstance(spy))
                .isSameInstanceAs(spied);
    }

    private static final class VoidAnswer implements Answer<Void> {
        @Nullable public InvocationOnMock invocation;

        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
            this.invocation = invocation;
            return null;
        }
    }

    interface Fixture {
        void voidTheVoidToAVoidVoidances();

        void pEquals(String arg);

        void toInfinity(String arg1, String arg2);

        void iJustCalled(Object... args);
    }
}
