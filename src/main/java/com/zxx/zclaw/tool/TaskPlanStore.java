package com.zxx.zclaw.tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persists task breakdown state under {@code <workDir>/.zclaw/task_plan.json}.
 */
public final class TaskPlanStore {

    private static final String DIR = ".zclaw";
    private static final String FILE = "task_plan.json";

    private final Path stateFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String goal = "";
    private final List<Step> steps = new ArrayList<>();

    public TaskPlanStore(Path workDir) {
        this.stateFile = workDir.resolve(DIR).resolve(FILE).normalize();
        load();
    }

    public synchronized void load() {
        goal = "";
        steps.clear();
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            String json = Files.readString(stateFile, StandardCharsets.UTF_8);
            Snapshot snap = gson.fromJson(json, Snapshot.class);
            if (snap != null) {
                if (snap.goal != null) {
                    goal = snap.goal;
                }
                if (snap.steps != null) {
                    for (Step s : snap.steps) {
                        if (s != null && s.title != null && !s.title.isBlank()) {
                            if (s.status == null || s.status.isBlank()) {
                                s.status = "pending";
                            } else {
                                s.status = normalizeStatus(s.status);
                            }
                            if (s.detail == null) {
                                s.detail = "";
                            }
                            if (s.note == null) {
                                s.note = "";
                            }
                            steps.add(s);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // leave empty
        }
    }

    private void save() throws IOException {
        Files.createDirectories(stateFile.getParent());
        Snapshot snap = new Snapshot();
        snap.goal = goal;
        snap.steps = new ArrayList<>(steps);
        Files.writeString(stateFile, gson.toJson(snap), StandardCharsets.UTF_8);
    }

    public synchronized String getGoal() {
        return goal;
    }

    public synchronized List<Step> getStepsCopy() {
        return new ArrayList<>(steps);
    }

    public synchronized boolean hasPlan() {
        return !steps.isEmpty();
    }

    public synchronized String init(String newGoal, List<Step> newSteps) throws IOException {
        if (newGoal == null) {
            newGoal = "";
        }
        if (newSteps == null || newSteps.isEmpty()) {
            return "Error: task_plan init requires a non-empty steps list.";
        }
        goal = newGoal.trim();
        steps.clear();
        for (Step s : newSteps) {
            if (s.title == null || s.title.isBlank()) {
                continue;
            }
            s.status = normalizeStatus(s.status == null || s.status.isBlank() ? "pending" : s.status);
            if (s.detail == null) {
                s.detail = "";
            }
            if (s.note == null) {
                s.note = "";
            }
            steps.add(s);
        }
        if (steps.isEmpty()) {
            return "Error: no valid steps after init.";
        }
        save();
        return renderTextForModel();
    }

    public synchronized String updateStep(int index1Based, String status, String note) throws IOException {
        if (index1Based < 1 || index1Based > steps.size()) {
            return "Error: invalid step_index " + index1Based + " (plan has " + steps.size() + " steps).";
        }
        Step s = steps.get(index1Based - 1);
        s.status = normalizeStatus(status);
        if (note != null) {
            s.note = note.trim();
        }
        save();
        return renderTextForModel();
    }

    public synchronized String reset() throws IOException {
        goal = "";
        steps.clear();
        if (Files.isRegularFile(stateFile)) {
            Files.delete(stateFile);
        }
        return "Task plan cleared. No active breakdown.";
    }

    public synchronized String show() {
        if (steps.isEmpty()) {
            return "No active task plan. Use action=init with goal and steps to create one.";
        }
        return renderTextForModel();
    }

    private static String normalizeStatus(String raw) {
        if (raw == null) {
            return "pending";
        }
        String x = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (x) {
            case "pending", "todo", "open" -> "pending";
            case "in_progress", "progress", "active", "working" -> "in_progress";
            case "done", "complete", "completed", "finished" -> "done";
            case "blocked", "block", "stuck" -> "blocked";
            default -> "pending";
        };
    }

    /**
     * Plain text for tool result (model + formatter both consume this shape).
     */
    public synchronized String renderTextForModel() {
        StringBuilder sb = new StringBuilder();
        sb.append("ZCLAW_TASK_PLAN_V1\n");
        sb.append("goal: ").append(goal.isEmpty() ? "(no goal set)" : goal).append('\n');
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            sb.append("step: ").append(i + 1)
                    .append(" | status=").append(s.status)
                    .append(" | title=").append(escapePipe(s.title));
            if (s.detail != null && !s.detail.isBlank()) {
                sb.append(" | detail=").append(escapePipe(s.detail));
            }
            if (s.note != null && !s.note.isBlank()) {
                sb.append(" | note=").append(escapePipe(s.note));
            }
            sb.append('\n');
        }
        sb.append("ZCLAW_TASK_PLAN_END\n\n");
        sb.append(renderMarkdownSummary());
        return sb.toString();
    }

    private static String escapePipe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("|", "/").replace("\n", " ");
    }

    private String renderMarkdownSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Task breakdown\n\n");
        if (!goal.isEmpty()) {
            sb.append("**Goal:** ").append(goal).append("\n\n");
        }
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            sb.append(i + 1).append(". [").append(s.status).append("] ").append(s.title);
            if (s.detail != null && !s.detail.isBlank()) {
                sb.append(" — ").append(s.detail);
            }
            if (s.note != null && !s.note.isBlank()) {
                sb.append(" _(note: ").append(s.note).append(")_");
            }
            sb.append('\n');
        }
        sb.append("\nUpdate statuses with task_plan action=update_step (step_index, status). ");
        sb.append("Statuses: pending, in_progress, done, blocked.\n");
        return sb.toString();
    }

    static final class Step {
        String title;
        String detail;
        String status;
        String note;
    }

    private static final class Snapshot {
        String goal;
        List<Step> steps;
    }
}
