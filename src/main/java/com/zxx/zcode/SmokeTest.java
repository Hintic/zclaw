package com.zxx.zcode;

import com.zxx.zcode.config.AgentConfig;
import com.zxx.zcode.llm.LLMClient;
import com.zxx.zcode.llm.LLMClientFactory;
import com.zxx.zcode.llm.model.LLMResponse;
import com.zxx.zcode.llm.model.Message;
import com.zxx.zcode.llm.model.ToolCallInfo;
import com.zxx.zcode.tool.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-interactive smoke test: sends a single message to the LLM and prints the response.
 * Usage: java -cp z-code.jar com.zxx.zcode.SmokeTest [--base-url=...] [--model=...] [message]
 */
public class SmokeTest {

    public static void main(String[] args) throws Exception {
        AgentConfig config = AgentConfig.fromArgs(args);

        // Find the last non-flag arg as the test message
        String testMessage = "你好，请简单介绍一下你自己（一句话）";
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                testMessage = arg;
            }
        }

        System.out.println("=== z-code Smoke Test ===");
        System.out.println("Base URL : " + config.getBaseUrl());
        System.out.println("Model    : " + config.getModel());
        System.out.println("Provider : " + config.getApiProvider());
        System.out.println("Message  : " + testMessage);
        System.out.println();

        LLMClient client = LLMClientFactory.create(config);

        // Test 1: Simple chat (no tools)
        System.out.println("--- Test 1: Simple Chat ---");
        ToolRegistry emptyRegistry = new ToolRegistry();

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("You are a helpful assistant. Reply concisely."));
        messages.add(Message.user(testMessage));

        try {
            LLMResponse response = client.chat(messages, emptyRegistry);
            System.out.println("Response: " + response.getTextContent());
            System.out.println("Tokens: " + response.getInputTokens() + " in / " + response.getOutputTokens() + " out");
            System.out.println("✅ Test 1 PASSED");
        } catch (Exception e) {
            System.out.println("❌ Test 1 FAILED: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();

        // Test 2: Chat with tools (expect tool call for "read pom.xml")
        System.out.println("--- Test 2: Tool Call ---");
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(config.getWorkDir()));
        registry.register(new BashTool(config.getWorkDir()));

        List<Message> messages2 = new ArrayList<>();
        messages2.add(Message.system("You are a coding assistant. Use tools when needed."));
        messages2.add(Message.user("读一下当前目录的 pom.xml 文件"));

        try {
            LLMResponse response2 = client.chat(messages2, registry);

            if (response2.hasToolCalls()) {
                System.out.println("LLM requested tool calls:");
                for (ToolCallInfo tc : response2.getToolCalls()) {
                    System.out.println("  → " + tc.getName() + "(" + tc.getArgumentsJson() + ")");
                    String result = registry.execute(tc.getName(), tc.getArgumentsJson());
                    System.out.println("  Result preview: " + result.substring(0, Math.min(200, result.length())) + "...");
                }
                System.out.println("✅ Test 2 PASSED (tool call triggered)");
            } else {
                System.out.println("Response: " + response2.getTextContent());
                System.out.println("⚠️  Test 2: No tool call (LLM replied directly)");
            }
        } catch (Exception e) {
            System.out.println("❌ Test 2 FAILED: " + e.getMessage());
            e.printStackTrace();
        }

        client.shutdown();
        System.out.println("\n=== Done ===");
    }
}
