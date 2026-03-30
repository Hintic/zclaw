package com.zxx.zcode.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
    /** Optional process-wide override for config dir, resolved before loading config.json. */
    private static volatile Path configDirOverride;

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
    /** When true, register Playwright browser tool (Chromium). */
    private boolean browserEnabled;
    /**
     * Playwright browser channel: {@code chrome} uses installed Google Chrome; {@code msedge} uses Edge;
     * empty or {@code bundled} uses Playwright-managed Chromium (run {@code playwright install chromium}).
     */
    private String browserChannel;
    /** Shortcut habit simplification: full request first, suggest second, auto from third. */
    private boolean habitSimplifyEnabled;
    /** Whether AUTO execution is allowed after suggest-confirm learning. */
    private boolean habitAutoEnabled;
    /** Max chars considered a shorthand input. */
    private int habitShortInputMaxChars;

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
        this.browserEnabled = false;
        this.browserChannel = "chrome";
        this.habitSimplifyEnabled = true;
        this.habitAutoEnabled = true;
        this.habitShortInputMaxChars = 18;
    }

    public static AgentConfig fromArgs(String[] args) {
        AgentConfig config = new AgentConfig();

        // Pre-scan config-dir first so config.json load can honor it.
        applyConfigDirOverrideFromArgs(args);

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
        String envBrowser = System.getenv("ZCODE_BROWSER");
        if (envBrowser != null && !envBrowser.isEmpty()) {
            config.browserEnabled = Boolean.parseBoolean(envBrowser);
        }
        if (System.getenv("ZCODE_BROWSER_CHANNEL") != null) {
            config.browserChannel = System.getenv("ZCODE_BROWSER_CHANNEL").trim();
        }
        String envHabit = System.getenv("ZCODE_HABIT_SIMPLIFY");
        if (envHabit != null && !envHabit.isBlank()) {
            config.habitSimplifyEnabled = Boolean.parseBoolean(envHabit.trim());
        }
        String envHabitAuto = System.getenv("ZCODE_HABIT_AUTO");
        if (envHabitAuto != null && !envHabitAuto.isBlank()) {
            config.habitAutoEnabled = Boolean.parseBoolean(envHabitAuto.trim());
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
            } else if (arg.startsWith("--browser=")) {
                config.browserEnabled = Boolean.parseBoolean(arg.substring("--browser=".length()));
            } else if (arg.startsWith("--browser-channel=")) {
                config.browserChannel = arg.substring("--browser-channel=".length()).trim();
            } else if ("--browser-channel".equals(arg) && i + 1 < args.length) {
                config.browserChannel = args[++i].trim();
            } else if (arg.startsWith("--habit-simplify=")) {
                config.habitSimplifyEnabled = Boolean.parseBoolean(arg.substring("--habit-simplify=".length()));
            } else if (arg.startsWith("--habit-auto=")) {
                config.habitAutoEnabled = Boolean.parseBoolean(arg.substring("--habit-auto=".length()));
            } else if (arg.startsWith("--habit-short-max-chars=")) {
                try {
                    config.habitShortInputMaxChars = Math.max(2,
                            Integer.parseInt(arg.substring("--habit-short-max-chars=".length())));
                } catch (NumberFormatException e) {
                    log.warn("Invalid --habit-short-max-chars value: {}", arg);
                }
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
            } else if (arg.startsWith("--config-dir=")) {
                // Already handled in pre-scan; keep this branch for explicitness.
                setConfigDirOverride(arg.substring("--config-dir=".length()));
            } else if ("--config-dir".equals(arg)) {
                if (i + 1 < args.length) {
                    setConfigDirOverride(args[++i]);
                }
            }
        }

        config.soulProfile = SoulLoader.load(config.soulId, config.workDir);

        if (config.soulMailPollSeconds < 0) {
            config.soulMailPollSeconds = config.soulProfile.isDefault() ? 0 : DEFAULT_SOUL_MAIL_POLL_SECONDS;
        }

        log.info("Config loaded: apiProvider={}, model={}, baseUrl={}, webSearch={}, browser={} (channel={}), memory={}, habit={} (auto={}, shortMax={}), soul={}, soulMailPoll={}s",
                config.apiProvider, config.model, config.baseUrl, config.webSearchEnabled, config.browserEnabled,
                config.browserChannelDisplay(), config.memoryEnabled, config.habitSimplifyEnabled,
                config.habitAutoEnabled, config.habitShortInputMaxChars, config.soulProfile.getId(),
                config.soulMailPollSeconds);
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
            if (map.containsKey("browser_enabled")) {
                this.browserEnabled = Boolean.TRUE.equals(map.get("browser_enabled"));
            }
            if (map.containsKey("browser_channel") && map.get("browser_channel") != null) {
                this.browserChannel = map.get("browser_channel").toString().trim();
            }
            if (map.containsKey("habit_simplify_enabled")) {
                this.habitSimplifyEnabled = Boolean.TRUE.equals(map.get("habit_simplify_enabled"));
            }
            if (map.containsKey("habit_auto_enabled")) {
                this.habitAutoEnabled = Boolean.TRUE.equals(map.get("habit_auto_enabled"));
            }
            if (map.containsKey("habit_short_input_max_chars") && map.get("habit_short_input_max_chars") instanceof Number n) {
                this.habitShortInputMaxChars = Math.max(2, n.intValue());
            }
        } catch (JsonSyntaxException e) {
            log.error("Failed to parse config file (malformed JSON): {}", configFile, e);
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
        if (configDirOverride != null) {
            return configDirOverride;
        }
        String envDir = System.getenv("ZCODE_CONFIG_DIR");
        if (envDir != null && !envDir.isBlank()) {
            return Paths.get(envDir.trim());
        }
        String envHome = System.getenv("ZCODE_HOME");
        if (envHome != null && !envHome.isBlank()) {
            return Paths.get(envHome.trim()).resolve(".zcode");
        }
        // Default: config/data lives under the directory where `zcode` is executed.
        return Paths.get(System.getProperty("user.dir")).resolve(".zcode");
    }

    public static Path getConfigFile() {
        return getConfigDir().resolve("config.json");
    }

    private static void applyConfigDirOverrideFromArgs(String[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }
            if (arg.startsWith("--config-dir=")) {
                setConfigDirOverride(arg.substring("--config-dir=".length()));
                return;
            }
            if ("--config-dir".equals(arg) && i + 1 < args.length) {
                setConfigDirOverride(args[i + 1]);
                return;
            }
        }
    }

    private static void setConfigDirOverride(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        configDirOverride = Paths.get(rawPath.trim());
    }

    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public Path getWorkDir() { return workDir; }
    public int getMaxConversationMessages() { return maxConversationMessages; }
    public String getApiProvider() { return apiProvider; }
    public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public boolean isBrowserEnabled() { return browserEnabled; }
    public boolean isHabitSimplifyEnabled() { return habitSimplifyEnabled; }
    public boolean isHabitAutoEnabled() { return habitAutoEnabled; }
    public int getHabitShortInputMaxChars() { return Math.max(2, habitShortInputMaxChars); }

    /** Playwright launch channel, or empty / {@code bundled} for bundled Chromium. */
    public String getBrowserChannel() {
        return browserChannel == null ? "" : browserChannel.trim();
    }

    /** For logs only (bundled vs channel name). */
    public String browserChannelDisplay() {
        String c = getBrowserChannel();
        if (c.isEmpty() || "bundled".equalsIgnoreCase(c)) {
            return "bundled";
        }
        return c;
    }

    public void setBrowserChannel(String browserChannel) {
        this.browserChannel = browserChannel;
    }
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
    public void setBrowserEnabled(boolean browserEnabled) { this.browserEnabled = browserEnabled; }
    public void setHabitSimplifyEnabled(boolean habitSimplifyEnabled) { this.habitSimplifyEnabled = habitSimplifyEnabled; }
    public void setHabitAutoEnabled(boolean habitAutoEnabled) { this.habitAutoEnabled = habitAutoEnabled; }
    public void setHabitShortInputMaxChars(int habitShortInputMaxChars) {
        this.habitShortInputMaxChars = Math.max(2, habitShortInputMaxChars);
    }
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
