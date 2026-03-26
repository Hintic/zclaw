package com.zxx.zcode;

import com.zxx.zcode.agent.AgentLoop;
import com.zxx.zcode.cli.AgentCli;
import com.zxx.zcode.config.AgentConfig;
import com.zxx.zcode.llm.LLMClient;
import com.zxx.zcode.llm.LLMClientFactory;
import com.zxx.zcode.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class ZCodeMain {

    private static final Logger log = LoggerFactory.getLogger(ZCodeMain.class);

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

        // Initialize agent loop
        AgentLoop agentLoop = new AgentLoop(config, llmClient, toolRegistry, out);

        log.info("z-code started: provider={}, model={}, workDir={}",
                config.getApiProvider(), config.getModel(), config.getWorkDir());

        // Start CLI
        AgentCli cli = new AgentCli(
                agentLoop,
                config.getWorkDir(),
                config.getModel(),
                config.getSoulProfile(),
                out,
                config.getSoulMailPollSeconds());
        cli.run();

        // Cleanup
        llmClient.shutdown();
    }
}
