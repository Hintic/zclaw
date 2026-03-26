package com.zxx.zcode.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task breakdown and status tracking. Persists under {@code .zcode/task_plan.json}.
 */
public class TaskPlanTool implements Tool {

    private final TaskPlanStore store;

    public TaskPlanTool(Path workDir) {
        this.store = new TaskPlanStore(workDir);
    }

    @Override
    public String name() {
        return "task_plan";
    }

    @Override
    public String description() {
        return """
                Break a complex request into steps and track progress with visible status updates.
                You MUST call this for multi-step engineering work: first action=init with goal and steps,
                then action=update_step whenever a step starts (in_progress), completes (done), or blocks (blocked).
                Use action=show to read the current plan. Use action=reset to clear the plan.
                """.trim();
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of(
                "type", "string",
                "enum", List.of("init", "update_step", "show", "reset"),
                "description", "init: create/replace plan; update_step: set status; show: read plan; reset: clear"));
        props.put("goal", Map.of("type", "string", "description", "High-level goal (required for init)"));
        props.put("steps", Map.of(
                "type", "array",
                "description", "For init: list of step objects {title, detail?} or plain strings as titles",
                "items", Map.of("type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string"),
                                "detail", Map.of("type", "string")),
                        "required", List.of("title"))));
        props.put("step_index", Map.of("type", "integer", "description", "1-based index for update_step"));
        props.put("status", Map.of(
                "type", "string",
                "enum", List.of("pending", "in_progress", "done", "blocked"),
                "description", "New status for update_step"));
        props.put("note", Map.of("type", "string", "description", "Optional note for update_step"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("action"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String action = stringArg(args, "action");
        if (action == null || action.isBlank()) {
            return "Error: action is required.";
        }
        try {
            return switch (action.trim().toLowerCase()) {
                case "init" -> handleInit(args);
                case "update_step" -> handleUpdate(args);
                case "show" -> store.show();
                case "reset" -> store.reset();
                default -> "Error: unknown action '" + action + "'. Use init, update_step, show, or reset.";
            };
        } catch (IOException e) {
            return "Error: failed to persist task plan: " + e.getMessage();
        }
    }

    private String handleInit(Map<String, Object> args) throws IOException {
        String goal = stringArg(args, "goal");
        Object stepsObj = args.get("steps");
        List<TaskPlanStore.Step> steps = parseSteps(stepsObj);
        return store.init(goal != null ? goal : "", steps);
    }

    private String handleUpdate(Map<String, Object> args) throws IOException {
        int idx = intArg(args, "step_index", -1);
        String status = stringArg(args, "status");
        if (status == null || status.isBlank()) {
            return "Error: update_step requires status (pending|in_progress|done|blocked).";
        }
        String note = stringArg(args, "note");
        return store.updateStep(idx, status, note);
    }

    @SuppressWarnings("unchecked")
    private static List<TaskPlanStore.Step> parseSteps(Object stepsObj) {
        List<TaskPlanStore.Step> out = new ArrayList<>();
        if (!(stepsObj instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (o instanceof String s) {
                if (!s.isBlank()) {
                    TaskPlanStore.Step st = new TaskPlanStore.Step();
                    st.title = s.trim();
                    st.status = "pending";
                    st.detail = "";
                    st.note = "";
                    out.add(st);
                }
            } else if (o instanceof Map<?, ?> m) {
                Object t = m.get("title");
                if (t == null) {
                    continue;
                }
                String title = String.valueOf(t).trim();
                if (title.isEmpty()) {
                    continue;
                }
                TaskPlanStore.Step st = new TaskPlanStore.Step();
                st.title = title;
                Object d = m.get("detail");
                st.detail = d != null ? String.valueOf(d) : "";
                st.status = "pending";
                st.note = "";
                out.add(st);
            }
        }
        return out;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int intArg(Map<String, Object> args, String key, int defaultVal) {
        Object v = args.get(key);
        if (v == null) {
            return defaultVal;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
