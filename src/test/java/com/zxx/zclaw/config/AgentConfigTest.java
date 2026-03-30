package com.zxx.zclaw.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testDefaultValues() {
        AgentConfig config = new AgentConfig();
        assertEquals("openai", config.getApiProvider());
        assertTrue(config.isWebSearchEnabled());
        assertEquals(5, config.getWebSearchMaxUses());
        assertEquals(100, config.getMaxConversationMessages());
        assertNotNull(config.getWebSearchAllowedDomains());
        assertTrue(config.getWebSearchAllowedDomains().isEmpty());
        assertNotNull(config.getWebSearchBlockedDomains());
        assertTrue(config.getWebSearchBlockedDomains().isEmpty());
        assertEquals("gemini-2.5-flash", config.getWebSearchModel());
        assertTrue(config.isMemoryEnabled());
    }

    @Test
    void testFromArgs_apiProvider() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--api-provider=anthropic"});
        assertEquals("anthropic", config.getApiProvider());
    }

    @Test
    void testFromArgs_webSearchDisabled() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--web-search=false"});
        assertFalse(config.isWebSearchEnabled());
    }

    @Test
    void testFromArgs_allOptions() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{
                "--api-provider=anthropic",
                "--model=claude-sonnet-4-20250514",
                "--base-url=https://api.anthropic.com",
                "--api-key=sk-ant-test",
                "--web-search=true",
                "--max-messages=50"
        });

        assertEquals("anthropic", config.getApiProvider());
        assertEquals("claude-sonnet-4-20250514", config.getModel());
        assertEquals("https://api.anthropic.com", config.getBaseUrl());
        assertEquals("sk-ant-test", config.getApiKey());
        assertTrue(config.isWebSearchEnabled());
        assertEquals(50, config.getMaxConversationMessages());
    }

    @Test
    void testSetters() {
        AgentConfig config = new AgentConfig();
        config.setApiProvider("anthropic");
        assertEquals("anthropic", config.getApiProvider());

        config.setWebSearchEnabled(false);
        assertFalse(config.isWebSearchEnabled());

        config.setWebSearchMaxUses(10);
        assertEquals(10, config.getWebSearchMaxUses());

        config.setWebSearchModel("gemini-2.0-flash");
        assertEquals("gemini-2.0-flash", config.getWebSearchModel());
    }

    @Test
    void testFromArgs_webSearchModel() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--web-search-model=gemini-2.0-flash"});
        assertEquals("gemini-2.0-flash", config.getWebSearchModel());
    }

    @Test
    void testDefaultWebSearchModel() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{});
        assertEquals("gemini-2.5-flash", config.getWebSearchModel());
    }

    @Test
    void testFromArgs_soul() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--soul=architect"});
        assertEquals("architect", config.getSoulProfile().getId());
        assertEquals("Architect", config.getSoulProfile().getDisplayName());
    }

    @Test
    void testFromArgs_soulSpaceSeparated() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--soul", "cli_spacer_soul"});
        assertEquals("cli_spacer_soul", config.getSoulProfile().getId());
        assertEquals("Cli_spacer_soul", config.getSoulProfile().getDisplayName());
    }

    @Test
    void testFromArgs_memoryDisabled() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--memory=false"});
        assertFalse(config.isMemoryEnabled());
    }

    @Test
    void testFromArgs_soulMailPollDefaultsTenForNamedSoul() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--soul", "poll_auto_soul"});
        assertEquals(AgentConfig.DEFAULT_SOUL_MAIL_POLL_SECONDS, config.getSoulMailPollSeconds());
    }

    @Test
    void testFromArgs_soulMailPollExplicitZero() {
        AgentConfig config = AgentConfig.fromArgs(new String[]{"--soul", "poll_off", "--soul-mail-poll=0"});
        assertEquals(0, config.getSoulMailPollSeconds());
    }
}
