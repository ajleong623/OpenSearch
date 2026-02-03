/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.index.engine.knn;

/**
 * Enum representing the intended workload optimization a user wants their k-NN system to have. Based on this value,
 * default parameter resolution will be determined.
 */
public interface Mode {
    public String getName();
}
