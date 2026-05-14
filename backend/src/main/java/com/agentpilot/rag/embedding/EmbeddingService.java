package com.agentpilot.rag.embedding;

public interface EmbeddingService {
    double[] embed(String text);

    double cosine(double[] left, double[] right);

    String serialize(double[] vector);

    String serializeForPgVector(double[] vector);

    double[] deserialize(String value);

    String provider();

    String modelName();

    int dimension();

    boolean configured();
}
