package com.agentpilot.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentpilot.model")
public class ModelProperties {
    private String provider = "mock";
    private OpenAiCompatible openaiCompatible = new OpenAiCompatible();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OpenAiCompatible getOpenaiCompatible() {
        return openaiCompatible;
    }

    public void setOpenaiCompatible(OpenAiCompatible openaiCompatible) {
        this.openaiCompatible = openaiCompatible;
    }

    public static class OpenAiCompatible {
        private String baseUrl = "";
        private String apiKey = "";
        private String chatModel = "gpt-4o-mini";
        private double temperature = 0.2;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }
}
