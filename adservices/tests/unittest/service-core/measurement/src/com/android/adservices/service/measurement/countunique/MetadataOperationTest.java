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

package com.android.adservices.service.measurement.countunique;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.List;

public class MetadataOperationTest {

    @Test
    public void getOperationsFromHeader_forValidHeader_returnsOperations() {
        String header =
                "set;key=\"key1\";value=\"1\";ignore_if_present,"
                        + "set;key=\"key2\";value=\"2\","
                        + "delete;key=\"key1\"";

        List<MetadataOperation> operations = MetadataOperation.getOperationsFromHeader(header);
        assertThat(operations.size()).isEqualTo(3);

        int verifiedResults = 0;
        for (MetadataOperation operation : operations) {

            if (operation.getKey().equals("key1")) {
                if (operation.getType().equals(MetadataOperation.OperationType.set)
                        && operation.getValue().equals("1")
                        && operation.isIgnoreIfPresent()) {
                    verifiedResults++;
                }

                if (operation.getType().equals(MetadataOperation.OperationType.delete)) {
                    verifiedResults++;
                }
            }

            if (operation.getKey().equals("key2")
                    && operation.getType().equals(MetadataOperation.OperationType.set)
                    && operation.getValue().equals("2")
                    && !operation.isIgnoreIfPresent()) {
                verifiedResults++;
            }
        }
        assertThat(verifiedResults).isEqualTo(3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forInvalidHeader_throwsException() {
        String header = "INVALID";
        MetadataOperation.getOperationsFromHeader(header);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forEmptyKey_throwsException() {
        String header =
                "set;key=\"\";value=\"1\";ignore_if_present, set;"
                        + "key=\"key2\";value=\"2\", \n"
                        + "delete;key=\"key1\"";
        List<MetadataOperation> operations = MetadataOperation.getOperationsFromHeader(header);
        assertThat(operations.size()).isEqualTo(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forSetWithoutKey_throwsException() {
        String header =
                "set;value=\"1\";ignore_if_present,"
                        + "set;key=\"key2\";value=\"2\","
                        + "delete;key=\"key1\"";
        MetadataOperation.getOperationsFromHeader(header);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forSetWithoutValue_throwsException() {
        String header =
                "set;key=\"key1\";ignore_if_present,"
                        + "set;key=\"key2\";value=\"2\","
                        + "delete;key=\"key1\"";
        MetadataOperation.getOperationsFromHeader(header);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forNoOperation_throwsException() {

        String header =
                "set;key=\"key1\";value=\"1\";ignore_if_present,"
                        + "key=\"key2\";value=\"2\","
                        + "delete;key=\"key1\"";
        MetadataOperation.getOperationsFromHeader(header);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forDeleteWithoutKey_throwsException() {

        String header =
                "set;key=\"key1\";value=\"1\";ignore_if_present,"
                        + "set;key=\"key2\";value=\"2\","
                        + "delete";
        MetadataOperation.getOperationsFromHeader(header);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOperationsFromHeader_forInvalidOperation_throwsException() {

        String header = "<invalid>\";ignore_if_present," + "set;key=\"key2\";value=\"2\"";
        MetadataOperation.getOperationsFromHeader(header);
    }
}
