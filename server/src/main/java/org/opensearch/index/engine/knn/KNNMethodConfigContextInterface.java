/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.index.engine.knn;

import org.opensearch.Version;

/**
 * This object provides additional context that the user does not provide when {@link KNNMethodContext} is
 * created via parsing. The values in this object need to be dynamically set and calling code needs to handle
 * the possibility that the values have not been set.
 */
public interface KNNMethodConfigContextInterface {
    public VectorDataType getVectorDataType();

    public Integer getDimension();

    public Version getVersionCreated();

    public Mode getMode();

    public CompressionLevel getCompressionLevel();

    public void setVectorDataType(VectorDataType vectorDataType);

    public void setDimension(Integer dimension);

    public void setVersionCreated(Version versionCreated);

    public void setMode(Mode mode);

    public void setCompressionLevel(CompressionLevel compressionLevel);
}
