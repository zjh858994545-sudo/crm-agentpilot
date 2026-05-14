package com.agentpilot.rag.embedding;

import com.agentpilot.model.config.ModelProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

@Service
public class ConfigurableEmbeddingService implements EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(ConfigurableEmbeddingService.class);
    private static final int MOCK_DIMENSION = 16;

    private final ModelProperties properties;
    private final RestClient restClient;

    public ConfigurableEmbeddingService(ModelProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public double[] embed(String text) {
        if (realEmbeddingEnabled()) {
            return embedWithOpenAiCompatibleProvider(text);
        }
        return deterministicEmbedding(text, MOCK_DIMENSION);
    }

    @Override
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

    @Override
    public String serialize(double[] vector) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        return builder.toString();
    }

    @Override
    public String serializeForPgVector(double[] vector) {
        return "[" + serialize(vector) + "]";
    }

    @Override
    public double[] deserialize(String value) {
        if (value == null || value.isBlank()) {
            return new double[dimension()];
        }
        return Arrays.stream(value.split(","))
                .mapToDouble(Double::parseDouble)
                .toArray();
    }

    @Override
    public String provider() {
        return embeddingProperties().getProvider();
    }

    @Override
    public String modelName() {
        if ("openai-compatible".equalsIgnoreCase(provider())) {
            return embeddingProperties().getOpenaiCompatible().getEmbeddingModel();
        }
        return "deterministic-mock";
    }

    @Override
    public int dimension() {
        if ("openai-compatible".equalsIgnoreCase(provider())) {
            return embeddingProperties().getOpenaiCompatible().getDimensions();
        }
        return MOCK_DIMENSION;
    }

    @Override
    public boolean configured() {
        ModelProperties.Embedding.OpenAiCompatible config = embeddingProperties().getOpenaiCompatible();
        return "openai-compatible".equalsIgnoreCase(provider())
                && StringUtils.hasText(config.getBaseUrl())
                && StringUtils.hasText(config.getApiKey())
                && StringUtils.hasText(config.getEmbeddingModel());
    }

    private double[] embedWithOpenAiCompatibleProvider(String text) {
        ModelProperties.Embedding.OpenAiCompatible config = embeddingProperties().getOpenaiCompatible();
        Map<String, Object> body = Map.of(
                "model", config.getEmbeddingModel(),
                "input", text == null ? "" : text,
                "dimensions", config.getDimensions(),
                "encoding_format", "float"
        );
        try {
            JsonNode response = restClient.post()
                    .uri(embeddingsEndpoint(config.getBaseUrl()))
                    .headers(headers -> headers.setBearerAuth(config.getApiKey()))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode embedding = response == null ? null : response.at("/data/0/embedding");
            if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                log.warn("OpenAI-compatible embedding response did not include data[0].embedding. model={}",
                        config.getEmbeddingModel());
                return deterministicEmbedding(text, config.getDimensions());
            }
            double[] vector = new double[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i).asDouble();
            }
            if (vector.length != config.getDimensions()) {
                log.warn("OpenAI-compatible embedding dimension mismatch. expected={} actual={} model={}",
                        config.getDimensions(), vector.length, config.getEmbeddingModel());
            }
            return vector;
        } catch (RuntimeException ex) {
            log.warn("OpenAI-compatible embedding failed. provider={} model={} message={}",
                    provider(), config.getEmbeddingModel(), ex.getMessage());
            return deterministicEmbedding(text, config.getDimensions());
        }
    }

    private double[] deterministicEmbedding(String text, int dimension) {
        double[] vector = new double[Math.max(1, dimension)];
        if (text == null || text.isBlank()) {
            return vector;
        }
        text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .forEach(codePoint -> vector[Math.floorMod(codePoint, vector.length)] += 1.0);
        normalize(vector);
        return vector;
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

    private String embeddingsEndpoint(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/embeddings")) {
            return normalized;
        }
        return normalized + "/embeddings";
    }

    private boolean realEmbeddingEnabled() {
        return configured();
    }

    private ModelProperties.Embedding embeddingProperties() {
        return properties.getEmbedding();
    }
}
