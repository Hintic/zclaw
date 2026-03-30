package com.zxx.zclaw.memory;

import com.zxx.zclaw.llm.LLMClient;
import com.zxx.zclaw.llm.model.LLMResponse;
import com.zxx.zclaw.llm.model.Message;
import com.zxx.zclaw.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Calls the LLM to merge the latest turn into {@code memory.md} body (markdown).
 */
public final class MemorySummarizer {

    private static final Logger log = LoggerFactory.getLogger(MemorySummarizer.class);
    private static final int MAX_ASSISTANT_SNIPPET = 12_000;
    private static final int MAX_USER_SNIPPET = 4_000;
    private static final int MAX_EXISTING = 24_000;

    private static final String SUMMARY_SYSTEM = """
            You maintain a concise project memory file (markdown) for zclaw CLI.
            Merge the latest user turn into the existing memory content.
            - Preserve user preferences, conventions, important decisions, names, paths, and pitfalls.
            - Use short bullet lists and ## section headers where helpful.
            - Remove duplicates and outdated items when superseded.
            - Do not invent facts not present in the inputs.
            - Output ONLY the full markdown body for memory.md: no preamble, no wrapping the entire output in a fenced code block.
            - Prefer English for headings; keep the user's language for quoted preferences.
            - If the result would exceed 8000 characters, trim least important older bullets first.
            """;

    private final LLMClient llmClient;

    public MemorySummarizer(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * @param existingMemory current memory.md contents (may be empty)
     * @param userMessage    latest user message
     * @param assistantReply latest assistant reply shown to the user
     * @return merged markdown for memory.md
     */
    public String merge(String existingMemory, String userMessage, String assistantReply)
            throws IOException {
        return merge(existingMemory, userMessage, assistantReply, null);
    }

    /**
     * @param usageSink if non-null, invoked with the raw {@link LLMResponse} after the merge call (for session stats).
     */
    public String merge(String existingMemory, String userMessage, String assistantReply,
            Consumer<LLMResponse> usageSink)
            throws IOException {
        String existing = truncate(existingMemory == null ? "" : existingMemory, MAX_EXISTING);
        String user = truncate(userMessage == null ? "" : userMessage, MAX_USER_SNIPPET);
        String assistant = truncate(assistantReply == null ? "" : assistantReply, MAX_ASSISTANT_SNIPPET);

        String userBlock = """
                ## Existing memory.md
                %s

                ## Latest user message
                %s

                ## Latest assistant reply
                %s
                """.formatted(
                existing.isEmpty() ? "(empty — start a new memory.)" : existing,
                user.isEmpty() ? "(empty)" : user,
                assistant.isEmpty() ? "(empty)" : assistant);

        List<Message> messages = List.of(
                Message.system(SUMMARY_SYSTEM),
                Message.user(userBlock));

        LLMResponse response = llmClient.chat(messages, new ToolRegistry());
        if (usageSink != null) {
            usageSink.accept(response);
        }
        String raw = response.getTextContent() == null ? "" : response.getTextContent();
        String cleaned = stripOuterCodeFence(raw);
        if (cleaned.isBlank()) {
            log.info("Memory merge returned empty text; keeping previous memory.");
            return existingMemory != null ? existingMemory : "";
        }
        return cleaned;
    }

    private static String truncate(String s, int maxChars) {
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "\n\n…(truncated for memory merge)\n";
    }

    static String stripOuterCodeFence(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstNl = t.indexOf('\n');
        if (firstNl < 0) {
            return t;
        }
        t = t.substring(firstNl + 1);
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
