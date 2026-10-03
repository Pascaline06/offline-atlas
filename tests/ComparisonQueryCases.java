package org.offlineatlas;

import java.util.Arrays;

public final class ComparisonQueryCases {
    public static void main(String[] args) {
        assertPair("What is the difference between solar and wind power?","solar power","wind power");
        assertPair("Compare Tokyo and Kyoto for a first visit","Tokyo","Kyoto");
        assertPair("Compare Machu Picchu and Angkor Wat","Machu Picchu","Angkor Wat");
        assertPair("Photosynthesis vs respiration","Photosynthesis","respiration");
        assertPair("How do antibiotics and vaccines differ?","antibiotics","vaccines");
        assertPair("How do boiling and filtration differ in making water safer?","boiling","filtration");
        assertPair("Compare boiling and water filters","boiling","water filters");
        assertPair("Explain how malaria differs from dengue fever","malaria","dengue fever");
        if (ComparisonQuery.parse("Why is the sky blue?")!=null) throw new AssertionError("Not a comparison");
    }
    private static void assertPair(String question,String first,String second) {
        if (!Arrays.equals(new String[]{first,second},ComparisonQuery.parse(question)))
            throw new AssertionError(question+" -> "+Arrays.toString(ComparisonQuery.parse(question)));
    }
}
