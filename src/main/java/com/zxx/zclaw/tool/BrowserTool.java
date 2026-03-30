package com.zxx.zclaw.tool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Browser automation via Playwright. By default launches the system Google Chrome ({@code channel=chrome}).
 * Use {@code browser_channel} {@code bundled} (or empty) to use Playwright-managed Chromium instead; then run:
 * {@code mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"}
 */
public final class BrowserTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BrowserTool.class);
    private static final Object LOCK = new Object();
    private static final int MAX_TEXT_RETURN = 60_000;
    private static final Pattern ALLOWED_URL = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;

    private final Path workDir;
    /** Playwright channel: e.g. {@code chrome}, {@code msedge}; empty or {@code bundled} = bundled Chromium. */
    private final String channel;

    public BrowserTool(Path workDir, String channel) {
        this.workDir = workDir;
        this.channel = channel == null ? "" : channel.trim();
    }

    @Override
    public String name() {
        return "browser";
    }

    @Override
    public String description() {
        return "Control a local Chromium browser via Playwright. "
                + "Only http(s) URLs. Actions: launch, close, navigate, click, fill, wait_for, content, screenshot, evaluate. "
                + "Call launch (or navigate) first. Screenshots are written under the work directory.";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of(
                "type", "string",
                "description", "launch | close | navigate | click | fill | wait_for | content | screenshot | evaluate"));
        props.put("url", Map.of("type", "string", "description", "For navigate: absolute http(s) URL"));
        props.put("selector", Map.of("type", "string", "description", "CSS selector for click, fill, wait_for, optional content"));
        props.put("value", Map.of("type", "string", "description", "For fill: text to type"));
        props.put("headless", Map.of("type", "boolean", "description", "For launch: default true"));
        props.put("timeout_ms", Map.of("type", "integer", "description", "For wait_for: timeout (default 30000)"));
        props.put("path", Map.of("type", "string", "description", "For screenshot: relative path under work dir (e.g. out/shot.png)"));
        props.put("expression", Map.of("type", "string",
                "description", "For evaluate: JavaScript expression evaluated in page context (e.g. document.title)"));
        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("action"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String action = str(args.get("action")).toLowerCase(Locale.ROOT).trim();
        if (action.isEmpty()) {
            return "Error: action is required";
        }
        try {
            return switch (action) {
                case "launch" -> doLaunch(args);
                case "close" -> doClose();
                case "navigate" -> doNavigate(str(args.get("url")));
                case "click" -> doClick(str(args.get("selector")));
                case "fill" -> doFill(str(args.get("selector")), str(args.get("value")));
                case "wait_for" -> doWaitFor(str(args.get("selector")), timeoutMs(args, 30_000));
                case "content" -> doContent(str(args.get("selector")));
                case "screenshot" -> doScreenshot(str(args.get("path")));
                case "evaluate" -> doEvaluate(str(args.get("expression")));
                default -> "Error: unknown action '" + action + "'";
            };
        } catch (Exception e) {
            log.error("browser {} failed: {}", action, e.getMessage());
            return "Error: browser " + action + " failed: " + e.getMessage();
        }
    }

    /** Idempotent shutdown for JVM exit or CLI end. */
    public static void shutdownQuietly() {
        synchronized (LOCK) {
            try {
                if (page != null) {
                    page.close();
                }
            } catch (Exception e) {
                log.info("browser page close: {}", e.getMessage());
            } finally {
                page = null;
            }
            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception e) {
                log.info("browser context close: {}", e.getMessage());
            } finally {
                context = null;
            }
            try {
                if (browser != null) {
                    browser.close();
                }
            } catch (Exception e) {
                log.info("browser close: {}", e.getMessage());
            } finally {
                browser = null;
            }
            try {
                if (playwright != null) {
                    playwright.close();
                }
            } catch (Exception e) {
                log.info("playwright close: {}", e.getMessage());
            } finally {
                playwright = null;
            }
        }
    }

    private String doLaunch(Map<String, Object> args) {
        boolean headless = true;
        if (args.get("headless") instanceof Boolean b) {
            headless = b;
        }
        synchronized (LOCK) {
            ensureBrowsers(headless);
        }
        return "Browser launched (headless=" + headless + "). Ready for navigate.";
    }

    private String doClose() {
        shutdownQuietly();
        return "Browser closed.";
    }

    private String doNavigate(String url) {
        if (url == null || url.isBlank()) {
            return "Error: url is required for navigate";
        }
        if (!ALLOWED_URL.matcher(url.trim()).find()) {
            return "Error: only http(s) URLs are allowed";
        }
        synchronized (LOCK) {
            Page p = ensureBrowsers(true);
            log.info("browser navigate: {}", url);
            p.navigate(url.trim());
            return "Navigated to " + url.trim() + " (title: " + nullSafeTitle(p) + ")";
        }
    }

    private String doClick(String selector) {
        if (selector == null || selector.isBlank()) {
            return "Error: selector is required for click";
        }
        synchronized (LOCK) {
            requirePage();
            log.info("browser click: {}", selector);
            page.locator(selector).click();
            return "Clicked: " + selector;
        }
    }

    private String doFill(String selector, String value) {
        if (selector == null || selector.isBlank()) {
            return "Error: selector is required for fill";
        }
        if (value == null) {
            value = "";
        }
        synchronized (LOCK) {
            requirePage();
            log.info("browser fill: {}", selector);
            page.locator(selector).fill(value);
            return "Filled: " + selector;
        }
    }

    private String doWaitFor(String selector, int timeoutMs) {
        if (selector == null || selector.isBlank()) {
            return "Error: selector is required for wait_for";
        }
        synchronized (LOCK) {
            requirePage();
            log.info("browser wait_for: {}", selector);
            page.locator(selector).waitFor(new Locator.WaitForOptions()
                    .setTimeout(timeoutMs)
                    .setState(WaitForSelectorState.VISIBLE));
            return "Visible: " + selector;
        }
    }

    private String doContent(String selector) {
        synchronized (LOCK) {
            requirePage();
            String text;
            if (selector != null && !selector.isBlank()) {
                text = page.locator(selector).innerText();
            } else {
                text = page.innerText("body");
            }
            if (text == null) {
                text = "";
            }
            if (text.length() > MAX_TEXT_RETURN) {
                return text.substring(0, MAX_TEXT_RETURN) + "\n... [truncated, " + text.length() + " chars]";
            }
            return text.isEmpty() ? "(empty)" : text;
        }
    }

    private String doScreenshot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Error: path is required for screenshot (relative to work dir)";
        }
        Path out = resolveWorkPath(relativePath);
        synchronized (LOCK) {
            requirePage();
            try {
                Path parent = out.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (IOException e) {
                return "Error: cannot create screenshot directory: " + e.getMessage();
            }
            log.info("browser screenshot: {}", out);
            page.screenshot(new Page.ScreenshotOptions().setPath(out));
            return "Screenshot saved: " + out.toAbsolutePath();
        }
    }

    private String doEvaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return "Error: expression is required for evaluate";
        }
        synchronized (LOCK) {
            requirePage();
            Object raw = page.evaluate(expression);
            return raw == null ? "(null)" : String.valueOf(raw);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int timeoutMs(Map<String, Object> args, int defaultMs) {
        if (args.get("timeout_ms") instanceof Number n) {
            return Math.max(1000, n.intValue());
        }
        return defaultMs;
    }

    private void requirePage() {
        if (page == null) {
            throw new IllegalStateException("No browser page; call browser with action=launch or navigate first");
        }
    }

    private Path resolveWorkPath(String relativePath) {
        Path p = workDir.resolve(relativePath.trim()).normalize();
        if (!p.startsWith(workDir.normalize())) {
            throw new IllegalArgumentException("Path escapes work directory");
        }
        return p;
    }

    private Page ensureBrowsers(boolean headless) {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        if (browser == null) {
            BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions().setHeadless(headless);
            String ch = channel;
            if (ch != null && !ch.isEmpty() && !ch.equalsIgnoreCase("bundled")) {
                if ("chrome".equalsIgnoreCase(ch)) {
                    Path localChrome = detectLocalChrome();
                    if (localChrome != null) {
                        opts.setExecutablePath(localChrome);
                        log.info("browser launch: local Chrome executable={}", localChrome);
                    } else {
                        opts.setChannel(ch);
                        log.info("browser launch: local Chrome not found, fallback Playwright channel={}", ch);
                    }
                } else {
                    opts.setChannel(ch);
                    log.info("browser launch: Playwright channel={}", ch);
                }
            } else {
                log.info("browser launch: bundled Chromium (install with: mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args=\"install chromium\")");
            }
            browser = playwright.chromium().launch(opts);
        }
        if (context == null) {
            context = browser.newContext();
        }
        if (page == null) {
            page = context.newPage();
        }
        return page;
    }

    /**
     * Best-effort local Chrome discovery to avoid Playwright downloading Chromium.
     */
    private static Path detectLocalChrome() {
        List<Path> candidates = List.of(
                Paths.get("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
                Paths.get(System.getProperty("user.home"), "Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
                Paths.get("/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary"),
                Paths.get(System.getProperty("user.home"), "Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary"),
                Paths.get("/usr/bin/google-chrome"),
                Paths.get("/usr/bin/google-chrome-stable"),
                Paths.get("/opt/google/chrome/chrome"),
                Paths.get("/snap/bin/chromium"),
                Paths.get("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"),
                Paths.get("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe")
        );
        for (Path p : candidates) {
            try {
                if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                    return p;
                }
            } catch (Exception ignored) {
                // Keep probing other common locations.
            }
        }
        return null;
    }

    private static String nullSafeTitle(Page p) {
        try {
            return p.title();
        } catch (Exception e) {
            return "?";
        }
    }
}
