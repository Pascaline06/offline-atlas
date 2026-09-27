package org.offlineatlas;

import java.util.Arrays;

public final class ComparisonQueryCases {
    public static void main(String[] args) {
        assertPair("What is the difference between solar and wind power?","solar power","wind power");
        assertPair("Compare Tokyo and Kyoto for a first visit","Tokyo","Kyoto");
        assertPair("Compare Machu Picchu and Angkor Wat","Machu Picchu","Angkor Wat");
        assertPair("Photosynthesis vs respiration","Photosynthesis","respiration");
        if (ComparisonQuery.parse("Why is the sky blue?")!=null) throw new AssertionError("Not a comparison");
    }
    private static void assertPair(String question,String first,String second) {
        if (!Arrays.equals(new String[]{first,second},ComparisonQuery.parse(question)))
            throw new AssertionError(question+" -> "+Arrays.toString(ComparisonQuery.parse(question)));
    }
}
