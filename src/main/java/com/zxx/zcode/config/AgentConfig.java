package com.zxx.zcode.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zxx.zcode.soul.SoulLoader;
import com.zxx.zcode.soul.SoulProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /** When unset, non-default souls auto-enable soul_inbox polling at this interval (seconds). */
    public static final int DEFAULT_SOUL_MAIL_POLL_SECONDS = 10;

    private String baseUrl;
    private String apiKey;
    private String model;
    private Path workDir;
    private int maxConversationMessages;
    private String apiProvider;
    private boolean webSearchEnabled;
    private int webSearchMaxUses;
    private List<String> webSearchAllowedDomains;
    private List<String> webSearchBlockedDomains;
    private String webSearchModel;
    /** When true, load {@code memory.md} into system prompt and refresh it after each assistant reply. */
    private boolean memoryEnabled;
    /** Optional soul id (persona + peer mail); JSON under workDir/.zcode/souls or ~/.zcode/souls */
    private String soulId;
    private SoulProfile soulProfile;
    /**
     * Interval in seconds to poll {@code soul_inbox} and auto-run {@code processInput} on new mail.
     * {@code -1} means unset (after {@link #fromArgs} resolves: 0 for default soul, else {@link #DEFAULT_SOUL_MAIL_POLL_SECONDS}).
     */
    private int soulMailPollSeconds;

    public AgentConfig() {
        this.baseUrl = "http://aigw.fx.ctripcorp.com/llm/100000420";
        this.apiKey = "";
        this.model = "MiniMax-M2-7";
        this.workDir = Paths.get(System.getProperty("user.dir"));
        this.maxConversationMessages = 100;
        this.apiProvider = "openai";
        this.webSearchEnabled = true;
        this.webSearchMaxUses = 5;
        this.webSearchAllowedDomains = new ArrayList<>();
        this.webSearchBlockedDomains = new ArrayList<>();
        this.webSearchModel = "gemini-2.5-flash";
        this.memoryEnabled = true;
        this.soulId = "";
        this.soulMailPollSeconds = -1;
    }

    public static AgentConfig fromArgs(String[] args) {
        AgentConfig config = new AgentConfig();

        // 1. Load from ~/.zcode/config.json (lowest priority)
        config.loadConfigFile();

        // 2. Override with environment variables
        String envKey = System.getenv("ZCODE_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            config.apiKey = envKey;
        }
        if (System.getenv("ZCODE_BASE_URL") != null) {
            config.baseUrl = System.getenv("ZCODE_BASE_URL");
        }
        if (System.getenv("ZCODE_MODEL") != null) {
            config.model = System.getenv("ZCODE_MODEL");
        }
        if (System.getenv("ZCODE_API_PROVIDER") != null) {
            config.apiProvider = System.getenv("ZCODE_API_PROVIDER");
        }
        String envMem = System.getenv("ZCODE_MEMORY");
        if (envMem != null && !envMem.isEmpty()) {
            config.memoryEnabled = Boolean.parseBoolean(envMem);
        }
        String envSoul = System.getenv("ZCODE_SOUL");
        if (envSoul != null && !envSoul.isBlank()) {
            config.soulId = normalizeSoulId(envSoul);
        }
        String envMailPoll = System.getenv("ZCODE_SOUL_MAIL_POLL");
        if (envMailPoll != null && !envMailPoll.isBlank()) {
            config.applySoulMailPollArg(envMailPoll);
        }

        // 3. Override with command line args (highest priority)
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--work-dir=")) {
                config.workDir = Paths.get(arg.substring("--work-dir=".length()));
            } else if (arg.startsWith("--base-url=")) {
                config.baseUrl = arg.substring("--base-url=".length());
            } else if (arg.startsWith("--api-key=")) {
                config.apiKey = arg.substring("--api-key=".length());
            } else if (arg.startsWith("--model=")) {
                config.model = arg.substring("--model=".length());
            } else if (arg.startsWith("--max-messages=")) {
                config.maxConversationMessages = Integer.parseInt(arg.substring("--max-messages=".length()));
            } else if (arg.startsWith("--api-provider=")) {
                config.apiProvider = arg.substring("--api-provider=".length());
            } else if (arg.startsWith("--web-search=")) {
                config.webSearchEnabled = Boolean.parseBoolean(arg.substring("--web-search=".length()));
            } else if (arg.startsWith("--web-search-model=")) {
                config.webSearchModel = arg.substring("--web-search-model=".length());
            } else if (arg.startsWith("--memory=")) {
                config.memoryEnabled = Boolean.parseBoolean(arg.substring("--memory=".length()));
            } else if (arg.startsWith("--soul=")) {
                config.soulId = normalizeSoulId(arg.substring("--soul=".length()));
            } else if ("--soul".equals(arg)) {
                if (i + 1 < args.length) {
                    config.soulId = normalizeSoulId(args[++i]);
                }
            } else if (arg.startsWith("-soul=")) {
                config.soulId = normalizeSoulId(arg.substring("-soul=".length()));
            } else if ("-soul".equals(arg)) {
                if (i + 1 < args.length) {
                    config.soulId = normalizeSoulId(args[++i]);
                }
            } else if (arg.startsWith("--soul-mail-poll=")) {
                config.applySoulMailPollArg(arg.substring("--soul-mail-poll=".length()));
            } else if ("--soul-mail-poll".equals(arg)) {
                if (i + 1 < args.length) {
                    config.applySoulMailPollArg(args[++i]);
                }
            }
        }

        config.soulProfile = SoulLoader.load(config.soulId, config.workDir);

        if (config.soulMailPollSeconds < 0) {
            config.soulMailPollSeconds = config.soulProfile.isDefault() ? 0 : DEFAULT_SOUL_MAIL_POLL_SECONDS;
        }

        log.info("Config loaded: apiProvider={}, model={}, baseUrl={}, webSearch={}, memory={}, soul={}, soulMailPoll={}s",
                config.apiProvider, config.model, config.baseUrl, config.webSearchEnabled, config.memoryEnabled,
                config.soulProfile.getId(), config.soulMailPollSeconds);
        return config;
    }

    private void applySoulMailPollArg(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            soulMailPollSeconds = Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            log.warn("Invalid soul mail poll seconds: {}", raw);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfigFile() {
        Path configFile = getConfigFile();
        if (!Files.exists(configFile)) return;
        try {
            String json = Files.readString(configFile);
            Map<String, Object> map = new Gson().fromJson(json,
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (map == null) return;

            if (map.containsKey("api_key")) this.apiKey = (String) map.get("api_key");
            if (map.containsKey("base_url")) this.baseUrl = (String) map.get("base_url");
            if (map.containsKey("model")) this.model = (String) map.get("model");
            if (map.containsKey("api_provider")) this.apiProvider = (String) map.get("api_provider");

            if (map.containsKey("web_search_enabled")) {
                this.webSearchEnabled = Boolean.TRUE.equals(map.get("web_search_enabled"));
            }
            if (map.containsKey("web_search_max_uses")) {
                this.webSearchMaxUses = ((Number) map.get("web_search_max_uses")).intValue();
            }
            if (map.containsKey("web_search_allowed_domains")) {
                this.webSearchAllowedDomains = (List<String>) map.get("web_search_allowed_domains");
            }
            if (map.containsKey("web_search_blocked_domains")) {
                this.webSearchBlockedDomains = (List<String>) map.get("web_search_blocked_domains");
            }
            if (map.containsKey("web_search_model")) {
                this.webSearchModel = (String) map.get("web_search_model");
            }
            if (map.containsKey("memory_enabled")) {
                this.memoryEnabled = Boolean.TRUE.equals(map.get("memory_enabled"));
            }
            if (map.containsKey("soul") && map.get("soul") != null) {
                String s = map.get("soul").toString();
                if (!s.isBlank()) {
                    this.soulId = normalizeSoulId(s);
                }
            }
            if (map.containsKey("soul_mail_poll_seconds") && map.get("soul_mail_poll_seconds") instanceof Number n) {
                this.soulMailPollSeconds = Math.max(0, n.intValue());
            }
        } catch (IOException e) {
            log.error("Failed to load config file: {}", configFile, e);
        }
    }

    /** Strips whitespace and optional matching ASCII quotes around CLI/env soul ids. */
    static String normalizeSoulId(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    public static Path getConfigDir() {
        return Paths.get(System.getProperty("user.home"), ".zcode");
    }

    public static Path getConfigFile() {
        return getConfigDir().resolve("config.json");
    }

    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public Path getWorkDir() { return workDir; }
    public int getMaxConversationMessages() { return maxConversationMessages; }
    public String getApiProvider() { return apiProvider; }
    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public int getWebSearchMaxUses() { return webSearchMaxUses; }
    public List<String> getWebSearchAllowedDomains() { return webSearchAllowedDomains; }
    public List<String> getWebSearchBlockedDomains() { return webSearchBlockedDomains; }
    public String getWebSearchModel() { return webSearchModel; }
    public boolean isMemoryEnabled() { return memoryEnabled; }
    public String getSoulId() {
        return getSoulProfile().getId();
    }
    public SoulProfile getSoulProfile() {
        return soulProfile != null ? soulProfile : SoulProfile.defaultProfile();
    }

    /** Seconds between soul_inbox polls; 0 disables. */
    public int getSoulMailPollSeconds() {
        return Math.max(0, soulMailPollSeconds);
    }

    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setModel(String model) { this.model = model; }
    public void setWorkDir(Path workDir) { this.workDir = workDir; }
    public void setApiProvider(String apiProvider) { this.apiProvider = apiProvider; }
    public void setWebSearchEnabled(boolean webSearchEnabled) { this.webSearchEnabled = webSearchEnabled; }
    public void setWebSearchMaxUses(int webSearchMaxUses) { this.webSearchMaxUses = webSearchMaxUses; }
    public void setWebSearchModel(String webSearchModel) { this.webSearchModel = webSearchModel; }
    public void setMemoryEnabled(boolean memoryEnabled) { this.memoryEnabled = memoryEnabled; }
    /** For tests: set both id and profile explicitly. */
    public void setSoulProfile(SoulProfile soulProfile) {
        this.soulProfile = soulProfile;
        this.soulId = soulProfile != null ? soulProfile.getId() : "";
    }

    /** For tests: explicit poll interval ({@code -1} = let {@link #fromArgs} apply defaults on next build). */
    public void setSoulMailPollSeconds(int soulMailPollSeconds) {
        this.soulMailPollSeconds = soulMailPollSeconds;
    }
}
