/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.index.engine.knn;

/**
 * Configuration for quantization
 */
public interface QuantizationConfig {
    public ScalarQuantizationType getQuantizationType();
}
