package org.offlineatlas;
import java.util.regex.Pattern;
/** Stable knowledge remains available; offline sources cannot establish live facts. */
final class QueryPolicy {
    private static final Pattern FRESH=Pattern.compile("(?is)(?:\\b(?:currently|today|tonight|right now|live|real.time|latest)\\b.{0,100}\\b(?:open|hours|menus?|weather|news|prices?|schedules?|availability|trains?|flights?)\\b|\\b(?:weather|news|prices?|schedules?|opening hours|availability)\\b.{0,100}\\b(?:today|tonight|right now|live|current|latest)\\b)");
    static boolean needsLiveData(String question) {return FRESH.matcher(question).find() || Pattern.compile("(?i)\\b(?:latest|right now|tonight)\\b|\\bcurrent\\b.{0,70}\\b(?:president|minister|leader|exchange rate|stock|inflation|version|news|score|price|menu|hours)\\b|\\b(?:weather forecast|stock price|exchange rate)\\b").matcher(question).find();}
    static String freshnessNotice() {return "I cannot verify live information offline. Local sources cannot confirm current hours, prices, weather, schedules, or availability.";}
    private QueryPolicy() { }
}
