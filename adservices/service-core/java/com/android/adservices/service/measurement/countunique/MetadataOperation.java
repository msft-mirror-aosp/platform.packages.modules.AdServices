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

import java.util.ArrayList;
import java.util.List;

public class MetadataOperation {
    public enum OperationType {
        set,
        delete;
    }

    private OperationType mType;
    private String mKey;
    private String mValue;
    private boolean mIgnoreIfPresent;

    public OperationType getType() {
        return mType;
    }

    public void setType(OperationType type) {
        this.mType = type;
    }

    public String getKey() {
        return mKey;
    }

    public void setKey(String key) {
        this.mKey = key;
    }

    public String getValue() {
        return mValue;
    }

    public void setValue(String value) {
        this.mValue = value;
    }

    public boolean isIgnoreIfPresent() {
        return mIgnoreIfPresent;
    }

    public void setIgnoreIfPresent(boolean ignoreIfPresent) {
        this.mIgnoreIfPresent = ignoreIfPresent;
    }

    private static final String OPERATIONS_SPLIT_CHARACTER = ",";
    private static final String OPERATION_PARTS_SPLIT_CHARACTER = ";";
    private static final String EQUAL = "=";
    private static final String FIELD_IGNORE_IF_PRESENT = "ignore_if_present";
    private static final String FIELD_KEY = "key";
    private static final String FIELD_VALUE = "value";

    /**
     * Process structured metadata header into a list of Metadata Operations
     *
     * @param metadataHeader - metadata header.
     * @return - list of parsed MetadataOperation
     */
    public static List<MetadataOperation> getOperationsFromHeader(String metadataHeader) {
        List<MetadataOperation> operations = new ArrayList<>();
        if (metadataHeader.isEmpty()) {
            return operations;
        }
        for (String operationStr : metadataHeader.trim().split(OPERATIONS_SPLIT_CHARACTER)) {
            MetadataOperation operation = parseOperation(operationStr);
            if (!validateOperation(operation)) {
                throw new IllegalArgumentException("Invalid Metadata operation");
            }
            operations.add(operation);
        }
        return operations;
    }

    /**
     * Process operation string to a MetadataOperation
     *
     * @param operationStr - Operation string to parse - set;key="k1";value="v1";ignore_if_present
     * @return - MetadataOperation object
     */
    private static MetadataOperation parseOperation(String operationStr) {
        MetadataOperation operation = new MetadataOperation();

        String[] operationParts = operationStr.trim().split(OPERATION_PARTS_SPLIT_CHARACTER);
        String operationType = operationParts[0].trim();

        operation.setType(OperationType.valueOf(operationType));
        for (int i = 1; i < operationParts.length; i++) {
            String operationPart = operationParts[i];

            if (FIELD_IGNORE_IF_PRESENT.equals(operationPart)) {
                operation.setIgnoreIfPresent(true);
                continue;
            }
            parseKeyOrValue(operation, operationPart);
        }
        return operation;
    }

    private static void parseKeyOrValue(MetadataOperation operation, String metadataStr) {
        // parse key or value of metadata here
        // key="exampleKey" or value="12"
        String[] metadata = metadataStr.trim().split(EQUAL);
        if (metadata.length == 2) {
            // split up key="exampleKey" into lhs and rhs. lhs is key and rhs is "exampleKey"
            String lhs = metadata[0];
            String rhs = metadata[1].replaceAll("\"", "");
            if (FIELD_KEY.equals(lhs)) {
                operation.setKey(rhs);
            } else if (FIELD_VALUE.equals(lhs)) {
                operation.setValue(rhs);
            }
        }
    }

    private static boolean validateOperation(MetadataOperation operation) {
        return validateSetOperation(operation) || validateDeleteOperation(operation);
    }

    private static boolean validateDeleteOperation(MetadataOperation operation) {
        return (operation.getType() == OperationType.delete)
                && (operation.getKey() != null)
                && (!operation.getKey().isEmpty());
    }

    private static boolean validateSetOperation(MetadataOperation operation) {
        return (operation.getType() == OperationType.set)
                && (operation.getKey() != null)
                && (!operation.getKey().isEmpty() && (operation.getValue() != null));
    }
}
