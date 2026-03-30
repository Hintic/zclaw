package com.zxx.zclaw.cli;

import com.zxx.zclaw.soul.SoulInboxStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SoulMailPollSchedulerTest {

    @Test
    void formatInboundAutoPromptIncludesFromAndBody() {
        List<SoulInboxStore.MailLine> lines = List.of(
                new SoulInboxStore.MailLine("cc", "gg", "[TASK_REPORT] do thing", 1L));
        String p = SoulMailPollScheduler.formatInboundAutoPrompt(lines, "gg");
        assertTrue(p.contains("cc"));
        assertTrue(p.contains("do thing"));
        assertTrue(p.contains("gg"));
    }
}
