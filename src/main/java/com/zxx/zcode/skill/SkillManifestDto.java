package com.zxx.zcode.skill;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

/**
 * Public metadata written to {@code .zcode/skill-manifests/{soulId}.json} for peer processes to read.
 * Does not include full skill bodies.
 */
public class SkillManifestDto {

    @SerializedName("soul_id")
    private String soulId;

    @SerializedName("updated_at_epoch_ms")
    private long updatedAtEpochMs;

    private List<Entry> skills = new ArrayList<>();

    public String getSoulId() {
        return soulId;
    }

    public void setSoulId(String soulId) {
        this.soulId = soulId;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public List<Entry> getSkills() {
        return skills;
    }

    public void setSkills(List<Entry> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public static class Entry {
        private String id;
        private String name;
        private String summary;

        @SerializedName("reference_files")
        private List<String> referenceFiles;

        @SerializedName("script_files")
        private List<String> scriptFiles;

        public Entry() {}

        public Entry(String id, String name, String summary, List<String> referenceFiles, List<String> scriptFiles) {
            this.id = id;
            this.name = name;
            this.summary = summary;
            this.referenceFiles = referenceFiles;
            this.scriptFiles = scriptFiles;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public List<String> getReferenceFiles() {
            return referenceFiles;
        }

        public void setReferenceFiles(List<String> referenceFiles) {
            this.referenceFiles = referenceFiles;
        }

        public List<String> getScriptFiles() {
            return scriptFiles;
        }

        public void setScriptFiles(List<String> scriptFiles) {
            this.scriptFiles = scriptFiles;
        }
    }
}
