package com.zxx.zcode.llm;

import com.zxx.zcode.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating the appropriate LLM client based on configuration.
 */
public class LLMClientFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMClientFactory.class);

    public static LLMClient create(AgentConfig config) {
        String provider = config.getApiProvider();
        switch (provider) {
            case "anthropic":
                log.info("Creating Anthropic Messages API client (web search enabled: {})",
                        config.isWebSearchEnabled());
                return new AnthropicLLMClient(config);
            case "openai":
            default:
                log.info("Creating OpenAI-compatible API client");
                return new OpenAILLMClient(config);
        }
    }
}
