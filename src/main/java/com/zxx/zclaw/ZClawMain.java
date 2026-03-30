package com.zxx.zclaw;

import com.zxx.zclaw.agent.AgentLoop;
import com.zxx.zclaw.cli.AgentCli;
import com.zxx.zclaw.config.AgentConfig;
import com.zxx.zclaw.habit.HabitEngine;
import com.zxx.zclaw.llm.LLMClient;
import com.zxx.zclaw.llm.LLMClientFactory;
import com.zxx.zclaw.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class ZClawMain {

    private static final Logger log = LoggerFactory.getLogger(ZClawMain.class);

    public static void main(String[] args) {
        AgentConfig config = AgentConfig.fromArgs(args);
        PrintStream out = System.out;

        // Initialize LLM client via factory
        LLMClient llmClient = LLMClientFactory.create(config);

        // Initialize tool registry
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool(config.getWorkDir()));
        toolRegistry.register(new WriteFileTool(config.getWorkDir()));
        toolRegistry.register(new EditFileTool(config.getWorkDir()));
        toolRegistry.register(new BashTool(config.getWorkDir()));
        toolRegistry.register(new GlobTool(config.getWorkDir()));
        toolRegistry.register(new GrepTool(config.getWorkDir()));
        toolRegistry.register(new TaskPlanTool(config.getWorkDir()));
        toolRegistry.register(new SoulMailTool(config.getWorkDir(), config.getSoulProfile()));
        toolRegistry.register(new SoulMoodTool(config.getWorkDir(), config.getSoulProfile()));

        // Register web search tool if enabled
        if (config.isWebSearchEnabled()) {
            toolRegistry.register(new WebSearchTool(
                    config.getBaseUrl(), config.getApiKey(), config.getWebSearchModel()));
            log.info("Web search enabled (model: {}, baseUrl: {})",
                    config.getWebSearchModel(), config.getBaseUrl());
        } else {
            log.info("Web search disabled");
        }

        if (config.isBrowserEnabled()) {
            toolRegistry.register(new BrowserTool(config.getWorkDir(), config.getBrowserChannel()));
            log.info("Browser automation enabled (Playwright, channel={})", config.browserChannelDisplay());
        } else {
            log.info("Browser automation disabled (set browser_enabled or ZCLAW_BROWSER=true or --browser=true)");
        }

        // Initialize agent loop
        AgentLoop agentLoop = new AgentLoop(config, llmClient, toolRegistry, out);
        HabitEngine habitEngine = new HabitEngine(
                config.getWorkDir(),
                config.getSoulId(),
                config.isHabitSimplifyEnabled(),
                config.isHabitAutoEnabled(),
                config.getHabitShortInputMaxChars());

        log.info("zclaw started: provider={}, model={}, workDir={}",
                config.getApiProvider(), config.getModel(), config.getWorkDir());

        // Start CLI
        AgentCli cli = new AgentCli(
                agentLoop,
                config.getWorkDir(),
                config.getModel(),
                config.getSoulProfile(),
                out,
                config.getSoulMailPollSeconds(),
                habitEngine);
        try {
            cli.run();
        } finally {
            BrowserTool.shutdownQuietly();
            llmClient.shutdown();
        }
    }
}
