package com.ivan.nexus.application.security;

/**
 * Outbound port for LLM summarization of security findings.
 * Implementations must never remediate or modify project code.
 */
public interface LlmClient {
    /**
     * Returns a short natural-language summary of the given prompt text.
     */
    String summarize(String prompt);
}
