package com.agentpilot.rag.embedding;

import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class MockEmbeddingService {
    private static final int DIMENSION = 16;

    public double[] embed(String text) {
        double[] vector = new double[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }
        text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(codePoint -> vector[Math.floorMod(codePoint, DIMENSION)] += 1.0);
        normalize(vector);
        return vector;
    }

    public double cosine(double[] left, double[] right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public String serialize(double[] vector) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(java.util.Locale.ROOT, "%.6f", vector[i]));
        }
        return builder.toString();
    }

    public double[] deserialize(String value) {
        if (value == null || value.isBlank()) {
            return new double[DIMENSION];
        }
        return Arrays.stream(value.split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
    }

    private void normalize(double[] vector) {
        double norm = Math.sqrt(Arrays.stream(vector).map(item -> item * item).sum());
        if (norm == 0.0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}

