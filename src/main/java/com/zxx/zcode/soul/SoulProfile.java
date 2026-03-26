package com.zxx.zcode.soul;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A "soul" is a named persona/configuration for one z-code process. Multiple processes in the same
 * {@code workDir} can use different souls and exchange messages via {@link SoulMailTool}.
 */
public final class SoulProfile {

    private final String id;
    private final String displayName;
    private final String persona;
    private final List<String> peers;

    public SoulProfile(String id, String displayName, String persona, List<String> peers) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
        this.persona = persona == null ? "" : persona;
        this.peers = peers == null ? List.of() : List.copyOf(peers);
    }

    public static SoulProfile defaultProfile() {
        return new SoulProfile("default", "z-code", "", List.of());
    }

    public boolean isDefault() {
        return "default".equals(id);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPersona() {
        return persona;
    }

    public List<String> getPeers() {
        return Collections.unmodifiableList(peers);
    }
}
