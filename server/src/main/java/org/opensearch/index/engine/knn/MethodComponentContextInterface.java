/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.index.engine.knn;

import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * MethodComponentContext represents a single user provided building block of a knn library index.
 *
 * Each component is composed of a name and a map of parameters.
 */
public interface MethodComponentContextInterface extends ToXContentFragment, Writeable {
    // private final Map<String, Object> parameters;

    public String getName();

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException;

    @Override
    public boolean equals(Object obj);

    @Override
    public int hashCode();

    /**
     * Gets the parameters of the component
     *
     * @return parameters
     */
    public Map<String, Object> getParameters();

    @Override
    public void writeTo(StreamOutput out) throws IOException;
}
