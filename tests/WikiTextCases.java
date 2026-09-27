package org.offlineatlas;

public final class WikiTextCases {
    public static void main(String[] args) {
        String raw="[[File:Wind.jpg|thumb|[[North Sea]] off [[Belgium]]]] A '''wind turbine''' converts moving air into electricity.";
        String actual=WikiText.excerpt(raw,"Wind turbine",800);
        if (!actual.equals("A wind turbine converts moving air into electricity."))
            throw new AssertionError(actual);
    }
}
