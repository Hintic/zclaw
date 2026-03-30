package com.zxx.zclaw.llm;

import com.zxx.zclaw.config.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LLMClientFactoryTest {

    @Test
    void testCreate_openai() {
        AgentConfig config = new AgentConfig();
        config.setApiProvider("openai");
        LLMClient client = LLMClientFactory.create(config);
        assertInstanceOf(OpenAILLMClient.class, client);
        client.shutdown();
    }

    @Test
    void testCreate_anthropic() {
        AgentConfig config = new AgentConfig();
        config.setApiProvider("anthropic");
        LLMClient client = LLMClientFactory.create(config);
        assertInstanceOf(AnthropicLLMClient.class, client);
        client.shutdown();
    }

    @Test
    void testCreate_default() {
        AgentConfig config = new AgentConfig();
        config.setApiProvider("unknown");
        LLMClient client = LLMClientFactory.create(config);
        assertInstanceOf(OpenAILLMClient.class, client);
        client.shutdown();
    }
}
