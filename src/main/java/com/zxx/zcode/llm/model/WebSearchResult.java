package com.zxx.zcode.llm.model;

/**
 * A single web search result from Claude's built-in web search tool.
 */
public class WebSearchResult {

    private final String query;
    private final String url;
    private final String title;
    private final String pageAge;

    public WebSearchResult(String query, String url, String title, String pageAge) {
        this.query = query;
        this.url = url;
        this.title = title;
        this.pageAge = pageAge;
    }

    public String getQuery() { return query; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getPageAge() { return pageAge; }

    @Override
    public String toString() {
        return "WebSearchResult{title='" + title + "', url='" + url + "'}";
    }
}
