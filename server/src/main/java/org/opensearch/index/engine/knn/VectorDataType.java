package org.opensearch.index.engine.knn;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.util.BytesRef;

/**
 * General interface for vector data types
 */
public interface VectorDataType {
    /**
     * Creates a KnnVectorFieldType based on the VectorDataType using the provided dimension and
     * VectorSimilarityFunction.
     *
     * @param dimension                   Dimension of the vector
     * @param knnVectorSimilarityFunction KNNVectorSimilarityFunction for a given spaceType
     * @return FieldType
     */
    public FieldType createKnnVectorFieldType(int dimension, KNNVectorSimilarityFunction knnVectorSimilarityFunction);

    /**
     * Deserializes float vector from BytesRef.
     *
     * @param binaryValue Binary Value
     * @return float vector deserialized from binary value
     */
    public float[] getVectorFromBytesRef(BytesRef binaryValue);
}
