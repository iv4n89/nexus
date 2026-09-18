package com.ivan.nexus.infrastructure.llm;

import com.ivan.nexus.application.security.LlmClient;

/**
 * Default LLM adapter: returns a deterministic local summary without calling any model.
 */
public class NoOpLlmClient implements LlmClient {
    @Override
    public String summarize(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "No finding details available.";
        }
        String trimmed = prompt.trim();
        if (trimmed.length() <= 400) {
            return "Summary (LLM disabled): " + trimmed;
        }
        return "Summary (LLM disabled): " + trimmed.substring(0, 400) + "...";
    }
}
