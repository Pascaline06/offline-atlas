package org.offlineatlas;

import java.util.List;

public final class VoyageListingsCases {
    public static void main(String[] args) {
        String article="{{eat | name=Green Table | content=Vegan café with {{lang|ja|パン}} and [[coffee|drinks]]. | address=5 River Rd | lastedit=2024-03-11 }} "
            + "{{eat | name=Closed Kitchen | content=Vegan food, permanently closed. }} "
            + "{{eat | name=Ordinary Cafe | content=Offers coffee and cake. }} "
            + "{{eat | name=Spliced | address=1 Main St … [later dining excerpts] | content=Vegan menu. }} "
            + "{{eat | name=No Options | content=No vegan options available. }} "
            + "{{eat | name=Vegan Food Cart | content=Free vegan meals on weekdays. }} "
            + "{{eat | name=Health Foods | content=Vegan groceries. }} "
            + "{{eat | name=Vegan Bakery | content=Vegan bakery selling bread. }} "
            + "{{eat | name=Broken | content=Vegan menu";
        List<VoyageListings.Lead> leads=VoyageListings.veganDining(article,10);
        if (leads.size()!=1 || !leads.get(0).name.equals("Green Table")
            || !leads.get(0).description.contains("Vegan café")
            || !leads.get(0).description.contains("drinks")
            || !leads.get(0).description.contains("5 River Rd")
            || !leads.get(0).description.contains("2024-03-11"))
            throw new AssertionError("Unexpected dining extraction: "+leads.size());
        if (!VoyageListings.veganDining(article,0).isEmpty()) throw new AssertionError("Limit ignored");
        if (!VoyageListings.veganDining("No relevant dining listings",10).isEmpty()) throw new AssertionError("False match");
        System.out.println("VoyageListings cases passed");
    }
}
