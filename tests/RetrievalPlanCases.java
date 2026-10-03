package org.offlineatlas;
public final class RetrievalPlanCases {
    public static void main(String[] args) {
        if(!RetrievalPlan.queries("How does it work?").isEmpty()) throw new AssertionError("Empty query produces sources");
        for(String query:RetrievalPlan.queries("water\" OR 1=1; -- filters"))
            if(query.contains("=") || query.contains(";") || query.contains("\"")) throw new AssertionError("Query injection");
        if(RetrievalPlan.coverage("Vaccines train immune cells.",RetrievalPlan.terms("How do vaccines train immunity?"))==0)
            throw new AssertionError("Useful overlap lost");
        if(QueryPolicy.needsLiveData("How do solar cells work?")) throw new AssertionError("Stable topic blocked");
        if(!QueryPolicy.needsLiveData("Which restaurants are currently open with confirmed menus?")) throw new AssertionError("Live facts accepted");
        if(RetrievalPlan.bm25(new byte[5])!=0) throw new AssertionError("Invalid matchinfo accepted");
        System.out.println("Query and freshness cases passed");
    }
}
