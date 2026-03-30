package com.zxx.zclaw.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskPlanToolTest {

    @Test
    void initAndUpdate() throws Exception {
        Path dir = Files.createTempDirectory("tpt");
        TaskPlanTool tool = new TaskPlanTool(dir);

        String r1 = tool.execute(Map.of(
                "action", "init",
                "goal", "Fix bug",
                "steps", List.of(
                        Map.of("title", "Repro"),
                        Map.of("title", "Patch", "detail", "edit foo"))));
        assertTrue(r1.contains("ZCLAW_TASK_PLAN_V1"));
        assertTrue(r1.contains("Fix bug"));
        assertTrue(r1.contains("Repro"));

        String r2 = tool.execute(Map.of(
                "action", "update_step",
                "step_index", 1,
                "status", "done"));
        assertTrue(r2.contains("status=done"));
        assertTrue(r2.contains("Repro"));

        String r3 = tool.execute(Map.of("action", "reset"));
        assertTrue(r3.contains("cleared") || r3.contains("No active"));
    }
}
