package org.offlineatlas;

public final class ResearchEvidenceCases {
    public static void main(String[] args) {
        String sky="Sky blue is a shade of light blue. The sky appears blue because sunlight scatters in the atmosphere.";
        String excerpt=ResearchEvidence.excerpt(sky,"Why is the sky blue?",550);
        if(!excerpt.contains("because sunlight scatters")) throw new AssertionError(excerpt);
        int answer=ResearchEvidence.score("Sky",sky,"Why is the sky blue?");
        int color=ResearchEvidence.score("Sky blue","Sky blue is a shade of light blue.","Why is the sky blue?");
        if(answer<=color) throw new AssertionError("Color page outranked explanation");
        String route="Most visitors arrive at Kyoto Station by Shinkansen (bullet train) from Tokyo. The fare is ¥14170.";
        String selected=ResearchEvidence.excerpt(route,"How do I get from Tokyo to Kyoto?",550);
        if(!selected.contains("Shinkansen") || selected.contains("¥14170")) throw new AssertionError(selected);
        if(!ResearchEvidence.sufficientlyCovered("Kyoto",selected,"How do I get from Tokyo to Kyoto?"))
            throw new AssertionError("Sourced route withheld");
        if(ResearchEvidence.sufficientlyCovered("Night","During a solar eclipse it is partially night.",
            "Can solar power work at night?")) throw new AssertionError("Irrelevant night page accepted");
        if(ResearchEvidence.sufficientlyCovered("The Fall of the Roman Empire (movie)",
            "The film depicts the fall of Rome.","Why did the Roman Empire fall?")) throw new AssertionError("Fiction accepted");
        if(!ResearchEvidence.sufficientlyCovered("Roman Empire", "The Western Empire deteriorated due to invasions.",
            "Why did the Roman Empire fall?")) throw new AssertionError("Causal passage withheld");
        if(ResearchEvidence.sufficientlyCovered("Soviet Union", "The Soviet Union collapsed in 1991. The revolution brought Lenin to power.",
            "Why did the Soviet Union collapse?")) throw new AssertionError("Unrelated cause accepted");
        if(ResearchEvidence.sufficientlyCovered("Blue", "The sky can be blue. Blue dye appears bright because it reflects light.",
            "Why is the sky blue?")) throw new AssertionError("Adjacent unrelated cause accepted");
        if(ResearchEvidence.sufficientlyCovered("List of Blue Sky Studios productions",
            "Originally produced at Blue Sky for release before cancellation due to closure.",
            "Why is the sky blue?")) throw new AssertionError("Studio catalogue accepted as sky evidence");
        String history="Many factors and events combined and finally they resulted in the dissolution of the Soviet Union. "
            +"Under glasnost, the government lost control over the media, exposing economic problems.";
        if(!ResearchEvidence.sufficientlyCovered("History of the Soviet Union (1985–1991)",
            ResearchEvidence.excerpt(history,"Why did the Soviet Union collapse?",1100),
            "Why did the Soviet Union collapse?")) throw new AssertionError("Dissolution evidence missed");
        if(ResearchEvidence.sufficientlyCovered("Soviet Union",
            "It collapsed in 1991. Lenin brought the Bolsheviks to power in 1917.",
            "Why did the Soviet Union collapse?")) throw new AssertionError("Unrelated historical cause accepted");
        if(ResearchEvidence.sufficientlyCovered("Tokyo/Shinagawa", "A bus goes from Shinagawa to Kyoto.",
            "How do I get from Tokyo to Kyoto?")) throw new AssertionError("Route without origin accepted");
        String table=WikiText.excerpt("Lead. {| style=\"width:100%\" | navigational junk |} "
            +"The Western Empire deteriorated due to invasions.","Roman Empire",550);
        if(table.contains("navigational junk") || !table.contains("deteriorated due to invasions"))
            throw new AssertionError("Wiki table leaked into evidence: "+table);
        String knowledgeTable=WikiText.excerpt("{| class=\"wikitable\" |+ Immunity || Antigens are introduced in vaccines. |}",
            "Adaptive immune system",550);
        if(!knowledgeTable.contains("Antigens are introduced in vaccines"))
            throw new AssertionError("Factual table lost: "+knowledgeTable);
        String vaccineArticle="The immune system prepares itself for future pathogens because it adapts. "
            +"Artificially acquired active immunity introduces antigens in vaccines. "
            +"The adaptive immune system remembers particular pathogens.";
        if(!ResearchEvidence.excerpt(vaccineArticle,"How do vaccines train the immune system?",550)
            .contains("antigens in vaccines")) throw new AssertionError("Vaccine evidence lost to generic passage");
        System.out.println("ResearchEvidence cases passed");
    }
}
