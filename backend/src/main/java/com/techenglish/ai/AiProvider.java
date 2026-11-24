package com.techenglish.ai;

public interface AiProvider {
    String name();

    String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens);
}
