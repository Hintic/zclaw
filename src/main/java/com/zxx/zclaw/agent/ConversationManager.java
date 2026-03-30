package com.zxx.zclaw.agent;

import com.zxx.zclaw.llm.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages conversation history with truncation to prevent context overflow.
 */
public class ConversationManager {

    private final List<Message> messages = new ArrayList<>();
    private final int maxMessages;
    private Message systemMessage;

    public ConversationManager(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public void setSystemMessage(Message systemMessage) {
        this.systemMessage = systemMessage;
    }

    public void addMessage(Message message) {
        messages.add(message);
        truncateIfNeeded();
    }

    public List<Message> getMessages() {
        List<Message> result = new ArrayList<>();
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        result.addAll(messages);
        return result;
    }

    public void clear() {
        messages.clear();
    }

    public int size() {
        return messages.size();
    }

    private void truncateIfNeeded() {
        if (messages.size() <= maxMessages) return;

        // Keep the most recent messages, but ensure we don't break tool call / tool result pairs.
        // Strategy: remove oldest messages, but never remove a tool result without its preceding assistant message.
        int toRemove = messages.size() - maxMessages;

        // Find a safe cut point: skip forward past any tool results at the cut boundary
        int cutPoint = toRemove;
        while (cutPoint < messages.size() && "tool".equals(messages.get(cutPoint).getRole())) {
            cutPoint++;
        }

        if (cutPoint > 0 && cutPoint < messages.size()) {
            messages.subList(0, cutPoint).clear();
        }
    }
}
