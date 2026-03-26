package com.zxx.zcode.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders markdown text to ANSI-colored terminal output.
 * Supports: code blocks, inline code, bold, headers, italic, links.
 * Keeps things simple with minimal dependencies.
 */
public class MarkdownRenderer {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String ITALIC = "\u001B[3m";
    private static final String UNDERLINE = "\u001B[4m";

    // Colors
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String WHITE = "\u001B[37m";
    private static final String GRAY = "\u001B[90m";

    // Pattern for code blocks (```language\n...```)
    private static final Pattern CODE_BLOCK = Pattern.compile(
            "(?s)(```(?:\\w*)\\n)(.*?)(```)");
    // Pattern for inline code (`code`)
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    // Pattern for bold (**text** or __text__)
    private static final Pattern BOLD_TEXT = Pattern.compile("(?:\\*\\*|__)(.+?)(?:\\*\\*|__)");
    // Pattern for italic (*text* or _text_ but not **)
    private static final Pattern ITALIC_TEXT = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
    // Pattern for links [text](url)
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)\\]\\(([^)]+)\\)");

    /**
     * Render markdown string to ANSI-colored terminal output.
     * Returns the rendered string (不含换行前缀).
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        String result = markdown;

        // Process in pass order: code blocks first (to protect code from other transforms)
        result = renderCodeBlocks(result);
        result = renderInlineCode(result);
        result = renderHeaders(result);
        result = renderBold(result);
        result = renderItalic(result);
        result = renderLinks(result);

        return result;
    }

    private String renderCodeBlocks(String text) {
        Matcher m = CODE_BLOCK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String lang = m.group(1).replace("```", "").replace("\n", "");
            String code = m.group(2);
            String langLabel = lang.isEmpty() ? "" : " " + lang;
            String rendered = CYAN + "┌─" + BOLD + langLabel + RESET + CYAN + "────────────────────────────────" + RESET + "\n" +
                    GREEN + code + RESET + "\n" +
                    CYAN + "└────────────────────────────────" + RESET;
            m.appendReplacement(sb, Matcher.quoteReplacement(rendered));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String renderInlineCode(String text) {
        Matcher m = INLINE_CODE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(DIM + "`" + WHITE + m.group(1) + DIM + "`" + RESET));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String renderHeaders(String text) {
        String result = text;
        // H3 first (###), then H2 (##), then H1 (#)
        result = result.replaceAll("(?m)^(### .+)$", BOLD + MAGENTA + "$1" + RESET);
        result = result.replaceAll("(?m)^(## .+)$", BOLD + CYAN + "$1" + RESET);
        result = result.replaceAll("(?m)^(# .+)$", BOLD + WHITE + "$1" + RESET);
        return result;
    }

    private String renderBold(String text) {
        Matcher m = BOLD_TEXT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(BOLD + m.group(1) + RESET));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String renderItalic(String text) {
        Matcher m = ITALIC_TEXT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(ITALIC + m.group(1) + RESET));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String renderLinks(String text) {
        Matcher m = LINK.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String label = m.group(1);
            String url = m.group(2);
            // Shorten long URLs for display
            String display = url.length() > 60 ? url.substring(0, 57) + "..." : url;
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    UNDERLINE + CYAN + label + RESET + GRAY + " (" + display + ")" + RESET));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
