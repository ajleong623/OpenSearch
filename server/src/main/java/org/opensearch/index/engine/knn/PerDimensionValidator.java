/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.index.engine.knn;

/**
 * Validates per dimension fields
 */
public interface PerDimensionValidator {
    /**
     * Validates the given float is valid for the configuration
     *
     * @param value to validate
     */
    default void validate(float value) {}

    /**
     * Validates the given float as a byte is valid for the configuration.
     *
     * @param value to validate
     */
    default void validateByte(float value) {}
}
