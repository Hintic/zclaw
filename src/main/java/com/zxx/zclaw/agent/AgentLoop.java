package com.zxx.zclaw.agent;

import com.zxx.zclaw.config.AgentConfig;
import com.zxx.zclaw.llm.LLMClient;
import com.zxx.zclaw.llm.model.LLMResponse;
import com.zxx.zclaw.llm.model.Message;
import com.zxx.zclaw.llm.model.ToolCallInfo;
import com.zxx.zclaw.llm.model.WebSearchResult;
import com.zxx.zclaw.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zxx.zclaw.cli.CliIndent;
import com.zxx.zclaw.cli.MarkdownRenderer;
import com.zxx.zclaw.cli.StreamThinkingStripper;
import com.zxx.zclaw.cli.ThinkingIndicator;
import com.zxx.zclaw.cli.ToolResultFormatter;
import com.zxx.zclaw.memory.MemorySummarizer;
import com.zxx.zclaw.memory.ProjectMemory;
import com.zxx.zclaw.skill.SkillService;
import com.zxx.zclaw.soul.SoulMoodStore;
import com.zxx.zclaw.soul.SoulProfile;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core ReAct loop: user input → LLM → tool calls → LLM → ... → text response.
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    private static final int MAX_TOOL_ITERATIONS = 25;
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConversationManager conversation;
    private final AgentConfig config;
    private final PrintStream out;
    private final ThinkingIndicator thinkingIndicator;
    private final ToolResultFormatter formatter;
    private final MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    private final AtomicLong sessionPromptTokens = new AtomicLong();
    private final AtomicLong sessionCompletionTokens = new AtomicLong();
    private final AtomicLong sessionCacheReadTokens = new AtomicLong();
    private final AtomicLong sessionCacheCreationTokens = new AtomicLong();
    private final AtomicLong sessionCacheHitDenominatorSum = new AtomicLong();

    public AgentLoop(AgentConfig config, LLMClient llmClient, ToolRegistry toolRegistry, PrintStream out) {
        this.config = config;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.out = out;
        this.thinkingIndicator = new ThinkingIndicator(out);
        this.formatter = new ToolResultFormatter();
        this.conversation = new ConversationManager(config.getMaxConversationMessages());
        setupSystemPrompt();
    }

    private void setupSystemPrompt() {
        Path workDir = config.getWorkDir();
        String systemPrompt = buildSystemPrompt(workDir);
        conversation.setSystemMessage(Message.system(systemPrompt));
    }

    private String buildSystemPrompt(Path workDir) {
        StringBuilder sb = new StringBuilder();
        SoulProfile soul = config.getSoulProfile();
        if (soul.isDefault() && soul.getPersona().isBlank()) {
            sb.append("You are zclaw, a coding assistant that helps users with software engineering tasks.\n");
        } else {
            sb.append("You are ").append(soul.getDisplayName());
            sb.append(", a zclaw assistant instance with soul id `").append(soul.getId()).append("`.\n");
            if (!soul.getPersona().isBlank()) {
                sb.append("\n## Persona\n");
                sb.append(soul.getPersona().trim()).append("\n\n");
            }
            sb.append("You follow the same engineering standards as zclaw for this workspace.\n");
        }
        sb.append("You operate in the following working directory: ").append(workDir.toAbsolutePath()).append("\n\n");

        sb.append("## Available Tools\n");
        sb.append("You have access to the following tools to help the user:\n");
        sb.append("- read_file: Read file contents with line numbers\n");
        sb.append("- write_file: Create or overwrite files\n");
        sb.append("- edit_file: Surgically edit files by replacing exact strings\n");
        sb.append("- bash: Execute shell commands\n");
        sb.append("- glob: Find files by name pattern\n");
        sb.append("- grep: Search file contents by regex\n");
        sb.append("- task_plan: Break work into steps and update status (init / update_step / show / reset); ");
        sb.append("use for multi-step tasks and keep the board accurate.\n");
        sb.append("- soul_mail: Message another zclaw process in this directory with a different --soul; ");
        sb.append("action=send (to_soul, message, optional kind=normal|task_report|review|skill_suggest) ");
        sb.append("or action=receive (limit, consume).\n");
        if (!soul.isDefault()) {
            sb.append("- soul_mood: Simulated mood 0–100 affects your tone; action=status or action=event with event=TASK_COMPLETED|");
            sb.append("USER_PRAISE|USER_CRITICISM|REVIEW_FOUND_ISSUES_IN_PEER_REPORT|REVIEW_PEER_REPORT_OK|");
            sb.append("PEER_REVIEW_FLAGGED_MY_ISSUES|CALM_BREAK. Register honestly when outcomes occur.\n");
        }
        if (config.isWebSearchEnabled() && "anthropic".equals(config.getApiProvider())) {
            sb.append("- web_search: Search the web for real-time information (built-in, auto-triggered)\n");
        }
        if (config.isBrowserEnabled()) {
            sb.append("- browser: Automate local browser (Playwright); default channel is Google Chrome when installed; ");
            sb.append("launch, navigate, click, fill, wait_for, content, screenshot, evaluate; only http(s) URLs; ");
            sb.append("screenshots under work dir\n");
        }
        sb.append("\n");

        sb.append("## Multi-instance collaboration\n");
        sb.append("The user may run a second zclaw in the same directory with another --soul. ");
        sb.append("Your soul id is `").append(soul.getId()).append("`. ");
        if (!soul.getPeers().isEmpty()) {
            sb.append("Configured peer soul ids (same machine, same work dir): ");
            sb.append(String.join(", ", soul.getPeers())).append(". ");
        }
        sb.append("Use soul_mail to coordinate; call receive when syncing or when the user asks what the peer sent.\n");
        if (config.getSoulMailPollSeconds() > 0) {
            sb.append("This instance auto-pulls your soul_mail inbox every ").append(config.getSoulMailPollSeconds());
            sb.append("s; user turns starting with [Automated soul_mail poll] are injected mail — handle them like peer handoffs.\n");
        }
        sb.append("\n");

        SkillService.appendSkillSections(sb, workDir, soul);

        if (!soul.isDefault()) {
            try {
                SoulMoodStore moodStore = new SoulMoodStore(workDir);
                SoulMoodStore.State st = moodStore.load(soul.getId());
                sb.append("## Mood (roleplay)\n");
                sb.append("Current mood score: ").append(st.score).append("/100. ");
                sb.append("Let this influence how you phrase replies (not what tools you run).\n");
                sb.append(SoulMoodStore.toneInstructionForScore(st.score, soul)).append("\n\n");
            } catch (IOException e) {
                log.error("Failed to load soul mood: {}", e.getMessage());
            }
        }

        if (SoulMoodStore.isGgCcSetup(soul)) {
            sb.append(SoulMoodStore.ggCcWorkflowBlock(soul));
        }

        sb.append("## Guidelines\n");
        sb.append("- Read files before modifying them.\n");
        sb.append("- Use edit_file for surgical edits instead of rewriting entire files.\n");
        sb.append("- Use glob and grep to explore the codebase before making changes.\n");
        sb.append("- Be concise in your responses. Lead with the answer or action.\n");
        sb.append("- When executing bash commands, prefer the working directory.\n");
        sb.append("- If a task is ambiguous, ask for clarification.\n");
        sb.append("- Reply directly. Do not include chain-of-thought or ");
        sb.append("machine-only reasoning tags; answer with the final user-visible text only.\n");
        sb.append("- For non-trivial multi-step work: call task_plan with action=init (goal + steps) first, ");
        sb.append("then action=update_step when a step becomes in_progress, done, or blocked. ");
        sb.append("The UI always shows a task board after each task_plan call.\n");
        if (!soul.getPeers().isEmpty()) {
            sb.append("- If the user's task clearly matches a peer's skill (see Peer souls' skills) better than yours, ");
            sb.append("send soul_mail with kind=skill_suggest instead of duplicating their specialty.\n");
        }

        if (config.isMemoryEnabled()) {
            try {
                String mem = ProjectMemory.load(workDir, soul);
                if (!mem.isEmpty()) {
                    sb.append("\n## Project memory (memory.md)\n");
                    sb.append("Persisted notes for this work directory (path depends on soul id). ");
                    sb.append("Use as context; do not repeat it verbatim unless helpful.\n\n");
                    sb.append(mem);
                    sb.append("\n");
                }
            } catch (IOException e) {
                log.error("Failed to read memory.md: {}", e.getMessage());
            }
        }

        return sb.toString();
    }

    /**
     * Process a single user input through the ReAct loop.
     * Returns when the LLM produces a text response (no more tool calls).
     */
    public String processInput(String userInput) throws IOException {
        setupSystemPrompt();
        conversation.addMessage(Message.user(userInput));

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            log.info("Agent loop iteration {} / {}", iteration + 1, MAX_TOOL_ITERATIONS);

            thinkingIndicator.start();
            final LLMResponse response;
            try {
                response = llmClient.chat(conversation.getMessages(), toolRegistry);
            } catch (IOException e) {
                thinkingIndicator.stop();
                throw e;
            }
            thinkingIndicator.stop();

            recordUsage(response);

            String rawText = response.getTextContent() == null ? "" : response.getTextContent();
            StreamThinkingStripper thinkingStripper = new StreamThinkingStripper();
            String visibleText = trimLeadingBlankLines(
                    thinkingStripper.feed(rawText) + thinkingStripper.drain());

            if (!visibleText.isEmpty()) {
                CliIndent.printlnIndented(out, markdownRenderer.render(visibleText));
                out.flush();
            }
            printWebSearchFootnotes(response);

            Message rawAssistant = response.getRawAssistantMessage();
            if (rawAssistant == null) {
                rawAssistant = Message.assistant(rawText, null);
            }
            conversation.addMessage(rawAssistant);

            if (!response.hasToolCalls()) {
                printUsage(response.getInputTokens(), response.getOutputTokens());
                maybePersistMemory(userInput, visibleText);
                return visibleText;
            }

            for (ToolCallInfo tc : response.getToolCalls()) {
                String toolName = tc.getName();
                String toolArgs = tc.getArgumentsJson();

                out.println(CliIndent.MARGIN + CYAN + "⚡ " + toolName + RESET);
                log.info("Tool call: {}({})", toolName, toolArgs);

                String result;
                thinkingIndicator.start(toolName + " running...");
                try {
                    result = toolRegistry.execute(toolName, toolArgs);
                } finally {
                    thinkingIndicator.stop();
                }
                log.info("Tool result: {} chars", result.length());

                String formatted = formatter.format(toolName, toolArgs, result);
                if (formatted != null && !formatted.isEmpty()) {
                    CliIndent.printlnIndented(out, formatted);
                    out.flush();
                }

                conversation.addMessage(llmClient.createToolResultMessage(tc.getId(), result));
            }
        }

        log.warn("Max tool iterations reached ({})", MAX_TOOL_ITERATIONS);
        return "[Max tool iterations reached (" + MAX_TOOL_ITERATIONS + "). Please try a simpler request.]";
    }

    /**
     * Merges this turn into workDir/memory.md and refreshes the system prompt for later turns.
     */
    private void maybePersistMemory(String userMessage, String assistantVisible) {
        if (!config.isMemoryEnabled()) {
            return;
        }
        if (assistantVisible == null || assistantVisible.isBlank()) {
            return;
        }
        try {
            SoulProfile soul = config.getSoulProfile();
            String cur = ProjectMemory.load(config.getWorkDir(), soul);
            MemorySummarizer summarizer = new MemorySummarizer(llmClient);
            String merged;
            thinkingIndicator.start("Updating memory.md...");
            try {
                merged = summarizer.merge(cur, userMessage, assistantVisible, this::recordUsage);
            } finally {
                thinkingIndicator.stop();
            }
            if (merged != null && !merged.isBlank()) {
                ProjectMemory.save(config.getWorkDir(), soul, merged);
                setupSystemPrompt();
                log.info("Updated project memory: {}", ProjectMemory.path(config.getWorkDir(), soul));
            }
        } catch (IOException e) {
            log.error("memory.md update failed: {}", e.getMessage());
        }
    }

    private void printWebSearchFootnotes(LLMResponse response) {
        if (!response.hasWebSearchResults()) {
            return;
        }
        List<WebSearchResult> results = response.getWebSearchResults();
        if (results == null) {
            return;
        }
        for (WebSearchResult r : results) {
            if (r == null) {
                continue;
            }
            String title = r.getTitle() != null ? r.getTitle() : "";
            String url = r.getUrl() != null ? r.getUrl() : "";
            if (title.isEmpty() && url.isEmpty()) {
                continue;
            }
            if (url.isEmpty()) {
                CliIndent.printlnIndented(out, title);
            } else if (title.isEmpty()) {
                CliIndent.printlnIndented(out, url);
            } else {
                CliIndent.printlnIndented(out, title + ": " + url);
            }
        }
        out.flush();
    }

    private void printUsage(int inputTokens, int outputTokens) {
        if (inputTokens > 0 || outputTokens > 0) {
            CliIndent.printlnIndented(out, "\u001B[90m[tokens: " + inputTokens + " in / " +
                    outputTokens + " out]\u001B[0m");
        }
    }

    /**
     * Adds one completion's usage to this CLI session (every agent round, isolated calls, memory merge).
     */
    void recordUsage(LLMResponse r) {
        if (r == null) {
            return;
        }
        int in = r.getInputTokens();
        int out = r.getOutputTokens();
        int cr = r.getCacheReadTokens();
        int cc = r.getCacheCreationTokens();
        int denom = r.getCacheHitDenominator();
        if (denom <= 0) {
            if (cr > 0 || cc > 0) {
                denom = in + cr + cc;
            } else {
                denom = in;
            }
        }
        if (in == 0 && out == 0 && cr == 0 && cc == 0) {
            return;
        }
        sessionPromptTokens.addAndGet(in);
        sessionCompletionTokens.addAndGet(out);
        sessionCacheReadTokens.addAndGet(cr);
        sessionCacheCreationTokens.addAndGet(cc);
        if (denom > 0) {
            sessionCacheHitDenominatorSum.addAndGet(denom);
        }
    }

    public SessionUsageTotals getSessionUsageTotals() {
        return new SessionUsageTotals(
                sessionPromptTokens.get(),
                sessionCompletionTokens.get(),
                sessionCacheReadTokens.get(),
                sessionCacheCreationTokens.get(),
                sessionCacheHitDenominatorSum.get());
    }

    public void clearHistory() {
        conversation.clear();
        setupSystemPrompt();
    }

    public int messageCount() {
        return conversation.size();
    }

    /**
     * Rough prompt size for the next model call (system + full history). Tokens ≈ chars / 4.
     * Optional {@code ZCLAW_CONTEXT_WINDOW} adds a %-of-window hint in {@link ConversationContextSnapshot#referencePercentSuffix()}.
     */
    public ConversationContextSnapshot getConversationContextSnapshot() {
        List<Message> msgs = conversation.getMessages();
        int chars = ConversationContextEstimator.approximateCharCount(msgs);
        int toks = ConversationContextEstimator.charsToApproxTokens(chars);
        int ref = ConversationContextEstimator.referenceContextWindowFromEnv();
        return new ConversationContextSnapshot(
                toks,
                chars,
                msgs.size(),
                config.getMaxConversationMessages(),
                ref);
    }

    /**
     * Isolated mode: send directly to LLM without tools, no conversation history.
     */
    public String processIsolated(String userInput) throws IOException {
        Message userMsg = Message.user(userInput);
        thinkingIndicator.start();
        final LLMResponse response;
        try {
            response = llmClient.chat(List.of(
                    Message.system(buildSystemPrompt(config.getWorkDir())),
                    userMsg), null);
        } catch (IOException e) {
            thinkingIndicator.stop();
            throw e;
        }
        thinkingIndicator.stop();

        recordUsage(response);

        String rawText = response.getTextContent() == null ? "" : response.getTextContent();
        StreamThinkingStripper thinkingStripper = new StreamThinkingStripper();
        String visibleText = trimLeadingBlankLines(
                thinkingStripper.feed(rawText) + thinkingStripper.drain());

        if (!visibleText.isEmpty()) {
            CliIndent.printlnIndented(out, markdownRenderer.render(visibleText));
            out.flush();
        }
        printWebSearchFootnotes(response);
        out.println();
        printUsage(response.getInputTokens(), response.getOutputTokens());
        return visibleText;
    }

    /** Drops leading empty lines so the assistant reply sits right under the ✻ header (no bogus gap). */
    private static String trimLeadingBlankLines(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        int start = 0;
        int len = s.length();
        while (start < len) {
            int lineEnd = s.indexOf('\n', start);
            if (lineEnd < 0) {
                String last = s.substring(start);
                return last.isBlank() ? "" : s.substring(start);
            }
            String line = s.substring(start, lineEnd);
            if (!line.isBlank()) {
                break;
            }
            start = lineEnd + 1;
        }
        return s.substring(start);
    }

    /**
     * Cumulative token accounting for the current CLI process (all LLM completions in this session).
     */
    public record SessionUsageTotals(
            long promptTokens,
            long completionTokens,
            long cacheReadTokens,
            long cacheCreationTokens,
            long cacheHitDenominatorSum) {

        public long totalTokens() {
            return promptTokens + completionTokens;
        }

        /** Percent of input-side tokens that were prompt-cache reads ({@code cache_read} / denom), or {@code n/a}. */
        public String cacheHitRatePercentOrDash() {
            if (cacheHitDenominatorSum <= 0) {
                return "n/a";
            }
            double pct = 100.0 * cacheReadTokens / cacheHitDenominatorSum;
            return String.format(Locale.US, "%.1f%%", pct);
        }
    }

    /**
     * Estimated size of the next chat request (conversation + system). Heuristic only.
     */
    public record ConversationContextSnapshot(
            int approxContextTokens,
            int approxContextChars,
            int messagesForNextRequest,
            int maxNonSystemMessagesCap,
            int referenceContextWindowTokens) {

        public String referencePercentSuffix() {
            if (referenceContextWindowTokens <= 0 || approxContextTokens <= 0) {
                return "";
            }
            double pct = 100.0 * approxContextTokens / referenceContextWindowTokens;
            return String.format(Locale.US, " (~%.1f%% of %s ref. window)",
                    pct,
                    String.format(Locale.US, "%,d", referenceContextWindowTokens));
        }
    }
}
