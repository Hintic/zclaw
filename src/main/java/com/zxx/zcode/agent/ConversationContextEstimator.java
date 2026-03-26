package com.zxx.zcode.agent;

import com.zxx.zcode.llm.model.ContentBlock;
import com.zxx.zcode.llm.model.Message;
import com.zxx.zcode.llm.model.ToolCall;

import java.util.List;
import java.util.Map;

/**
 * Rough size of the prompt that will be sent on the next turn (system + history).
 * Uses ~4 characters per token; real tokenization differs by model.
 */
public final class ConversationContextEstimator {

    /** Typical heuristic for Latin-heavy text; API counts will differ. */
    public static final int CHARS_PER_TOKEN_APPROX = 4;

    private static final int MESSAGE_JSON_OVERHEAD_CHARS = 24;

    private ConversationContextEstimator() {}

    public static int charsToApproxTokens(int chars) {
        if (chars <= 0) {
            return 0;
        }
        return (chars + CHARS_PER_TOKEN_APPROX - 1) / CHARS_PER_TOKEN_APPROX;
    }

    public static int approximateCharCount(Iterable<Message> messages) {
        int total = 0;
        for (Message m : messages) {
            total += MESSAGE_JSON_OVERHEAD_CHARS;
            total += approximateMessagePayloadChars(m);
        }
        return total;
    }

    /**
     * Optional reference window for /status (e.g. 200000). Set {@code ZCODE_CONTEXT_WINDOW} to a positive int.
     */
    public static int referenceContextWindowFromEnv() {
        String v = System.getenv("ZCODE_CONTEXT_WINDOW");
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int approximateMessagePayloadChars(Message m) {
        int n = 0;
        if (m.getRole() != null) {
            n += m.getRole().length();
        }
        if (m.getAnthropicContent() != null && !m.getAnthropicContent().isEmpty()) {
            for (ContentBlock b : m.getAnthropicContent()) {
                n += approximateContentBlockChars(b);
            }
        } else {
            String cs = m.getContentAsString();
            if (cs != null) {
                n += cs.length();
            } else if (m.getContent() != null) {
                n += stringifyContent(m.getContent()).length();
            }
        }
        if (m.getToolCalls() != null) {
            for (ToolCall tc : m.getToolCalls()) {
                n += 32;
                if (tc.getId() != null) {
                    n += tc.getId().length();
                }
                if (tc.getFunctionName() != null) {
                    n += tc.getFunctionName().length();
                }
                if (tc.getFunctionArguments() != null) {
                    n += tc.getFunctionArguments().length();
                }
            }
        }
        if (m.getToolCallId() != null) {
            n += m.getToolCallId().length();
        }
        return n;
    }

    private static int approximateContentBlockChars(ContentBlock b) {
        int n = 16;
        if (b.getType() != null) {
            n += b.getType().length();
        }
        if (b.getId() != null) {
            n += b.getId().length();
        }
        if (b.getName() != null) {
            n += b.getName().length();
        }
        if (b.getText() != null) {
            n += b.getText().length();
        }
        if (b.getThinking() != null) {
            n += b.getThinking().length();
        }
        if (b.getInput() != null) {
            n += b.getInput().toString().length();
        }
        if (b.getContent() != null) {
            for (ContentBlock.SearchResultEntry e : b.getContent()) {
                if (e == null) {
                    continue;
                }
                n += 24;
                if (e.getUrl() != null) {
                    n += e.getUrl().length();
                }
                if (e.getTitle() != null) {
                    n += e.getTitle().length();
                }
                if (e.getEncryptedContent() != null) {
                    n += Math.min(e.getEncryptedContent().length(), 2048);
                }
            }
        }
        return n;
    }

    private static String stringifyContent(Object content) {
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    sb.append(map.toString());
                } else if (o != null) {
                    sb.append(o.toString());
                }
                sb.append('\n');
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }
}
