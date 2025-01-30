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

package com.android.adservices.service.shell.attributionreporting;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.shell.NoOpShellCommand;
import com.android.adservices.service.shell.ShellCommand;
import com.android.adservices.service.shell.ShellCommandFactory;

import com.google.common.collect.Sets;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class AttributionReportingShellCommandFactoryTest extends AdServicesMockitoTestCase {
    private static boolean ATTRIBUTION_REPORTING_CLI_ENABLED = true;
    private static boolean ATTRIBUTION_REPORTING_CLI_DISABLED = false;
    private ShellCommandFactory mFactory;
    @Mock private DatastoreManager mDatastoreManager;
    @Mock private DevSessionDataStore mDevSessionDataStore;

    @Before
    public void setup() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_ENABLED, mDatastoreManager, mDevSessionDataStore);
    }

    @Test
    public void testGetShellCommand_listSourceRegistrationsCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListSourceRegistrationsCommand.CMD);
        assertThat(shellCommand)
                .isInstanceOf(AttributionReportingListSourceRegistrationsCommand.class);
    }

    @Test
    public void testGetShellCommand_listTriggerRegistrationsCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListTriggerRegistrationsCommand.CMD);
        assertThat(shellCommand)
                .isInstanceOf(AttributionReportingListTriggerRegistrationsCommand.class);
    }

    @Test
    public void testGetShellCommand_listEventReportsCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListEventReportsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(AttributionReportingListEventReportsCommand.class);
    }

    @Test
    public void testGetShellCommand_listAggregateReportsCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListAggregatableReportsCommand.CMD);
        assertThat(shellCommand)
                .isInstanceOf(AttributionReportingListAggregatableReportsCommand.class);
    }

    @Test
    public void testGetShellCommand_listDebugReportsCmd() {
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListDebugReportsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(AttributionReportingListDebugReportsCommand.class);
    }

    @Test
    public void testGetShellCommand_nullCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand(null);
        assertThat(shellCommand).isNull();
    }

    @Test
    public void testGetShellCommand_emptyCmd() {
        ShellCommand shellCommand = mFactory.getShellCommand("");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void testGetAttributionReportingListSourceRegistrationsCommand_invalidCmd() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_DISABLED,
                        mDatastoreManager,
                        mDevSessionDataStore);
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListSourceRegistrationsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void testGetAttributionReportingListTriggerRegistrationsCommand_invalidCmd() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_DISABLED,
                        mDatastoreManager,
                        mDevSessionDataStore);
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListTriggerRegistrationsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void testGetAttributionReportingListEventReportsCommand_invalidCmd() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_DISABLED,
                        mDatastoreManager,
                        mDevSessionDataStore);
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListEventReportsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void testGetAttributionReportingListAggregatableReportsCommand_invalidCmd() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_DISABLED,
                        mDatastoreManager,
                        mDevSessionDataStore);
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListAggregatableReportsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void testGetAttributionReportingListDebugReportsCommand_invalidCmd() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_DISABLED,
                        mDatastoreManager,
                        mDevSessionDataStore);
        ShellCommand shellCommand =
                mFactory.getShellCommand(AttributionReportingListDebugReportsCommand.CMD);
        assertThat(shellCommand).isInstanceOf(NoOpShellCommand.class);
    }

    @Test
    public void testGetShellCommand_invalidCmdCLIDisabled() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_DISABLED,
                        mDatastoreManager,
                        mDevSessionDataStore);
        ShellCommand shellCommand = mFactory.getShellCommand("invalid");
        assertThat(shellCommand).isNull();
    }

    @Test
    public void testGetCommandPrefix() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_ENABLED, mDatastoreManager, mDevSessionDataStore);
        assertThat(mFactory.getCommandPrefix())
                .isEqualTo(AttributionReportingShellCommandFactory.COMMAND_PREFIX);
    }

    @Test
    public void testGetAllCommandsHelp() {
        mFactory =
                new AttributionReportingShellCommandFactory(
                        ATTRIBUTION_REPORTING_CLI_ENABLED, mDatastoreManager, mDevSessionDataStore);

        assertThat(Sets.newHashSet(mFactory.getAllCommandsHelp()))
                .containsExactlyElementsIn(
                        Sets.newHashSet(
                                AttributionReportingListSourceRegistrationsCommand.HELP,
                                AttributionReportingListTriggerRegistrationsCommand.HELP,
                                AttributionReportingListEventReportsCommand.HELP,
                                AttributionReportingListAggregatableReportsCommand.HELP,
                                AttributionReportingListDebugReportsCommand.HELP));
    }
}
