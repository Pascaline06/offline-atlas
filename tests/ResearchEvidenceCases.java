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
        if(!ResearchEvidence.sufficientlyCovered("Florence",
            "By train: Frequent direct trains connect Florence with Rome.",
            "How can I travel from Rome to Florence by train?"))
            throw new AssertionError("Direct train service is bidirectional");
        String trainQuestion="How can I travel from Tokyo to Kyoto by train?";
        if(!ResearchEvidence.terms(trainQuestion).toString().equals("[tokyo, kyoto]"))
            throw new AssertionError("Route terms include mode instead of endpoints");
        if(!ResearchEvidence.sufficientlyCovered("Kyoto",
            "Take the Shinkansen train from Tokyo to Kyoto.",trainQuestion))
            throw new AssertionError("Train route withheld");
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
        if(ResearchEvidence.sufficientlyCovered("No fly list",
            "The list prevents some people from flying on airplanes.",
            "How do airplanes fly?")) throw new AssertionError("Unrelated flight page accepted");
        if(ResearchEvidence.sufficientlyCovered("Word processor",
            "A word processor is a computer program that edits text.",
            "How does a computer processor work?")) throw new AssertionError("Wrong type of processor accepted");
        if(ResearchEvidence.sufficientlyCovered("Norfolk Tides",
            "The Tides are named after the nearby bay because the team plays in Norfolk.",
            "Why do tides occur?")) throw new AssertionError("Unrelated team accepted");
        if(!ResearchEvidence.sufficientlyCovered("Earthquake",
            "Earthquakes are caused by tectonic movements in the Earth's crust.",
            "Why do earthquakes happen?")) throw new AssertionError("Singular subject missed");
        if(!ResearchEvidence.sufficientlyCovered("Rocket",
            "Rockets can be launched because exhaust thrust exceeds the weight of the vehicle.",
            "How do rockets launch?")) throw new AssertionError("Launch inflection withheld");
        if(!ResearchEvidence.sufficientlyCovered("Ice",
            "Ice floats on water because ice has less density than water.",
            "Why does ice float?")) throw new AssertionError("Exact subject evidence withheld");
        if(ResearchEvidence.sufficientlyCovered("Ice cream float",
            "An ice cream float is a drink made from ice cream and soda.",
            "Why does ice float?")) throw new AssertionError("Unrelated compound title accepted");
        if(ResearchEvidence.score("Vaccine",
            "Vaccines prepare the immune system against infection.",
            "How do vaccines work?") <= ResearchEvidence.score("HPV vaccine",
            "This vaccine is used because the virus can cause cancer.",
            "How do vaccines work?")) throw new AssertionError("General article lost to narrow vaccine");
        assertRejected("World War III","World War III could be caused by nuclear tensions.",
            "What caused World War I?");
        assertRejected("French Revolution of 1848","The French Revolution of 1848 was caused by a financial crisis.",
            "Why did the French Revolution happen?");
        assertRejected("French West Indies","The colonies rebelled, leading to the French Revolution.",
            "Why did the French Revolution happen?");
        assertRejected("Berlin Wall","The fall of the Berlin Wall led to German reunification.",
            "Why did the Berlin Wall fall?");
        assertRejected("Kyoto","The bus travels from Kyoto to Tokyo.",
            "How do I get from Tokyo to Kyoto?");
        assertRejected("Tokyo","A train runs from Tokyo to Kyoto.",
            "How do I get from Tokyo to Kyoto?");
        assertRejected("Kyoto Airport","A flight goes from Tokyo to Kyoto.",
            "How can I travel from Tokyo to Kyoto by train?");
        assertRejected("Immune system",
            "An immune system is vulnerable to infection because it attacks normal tissues.",
            "How does the immune system remember infections?");
        assertRejected("Computer",
            "The processor of a computer is made from integrated circuits and contains transistors.",
            "How does a computer processor work?");
        assertRejected("Train Stop – Two Minutes",
            "Train Stop – Two Minutes is a 1972 Soviet fantasy movie.",
            "How do trains stop?");
        assertRejected("American Civil War casualties",
            "The war caused many deaths and casualties.",
            "Why did the American Civil War begin?");
        assertRejected("African Americans in the American Civil War",
            "The American Civil War began due to slavery, and many African Americans fought in it.",
            "Why did the American Civil War begin?");
        assertAccepted("Volcano","Pressure builds as magma rises and causes volcanoes to erupt.",
            "Why do volcanoes erupt?");
        assertAccepted("Airplane","An airplane flies because lift from the wings counters gravity.",
            "How do airplanes fly?");
        assertAccepted("Airplane","Air flows over the wings, which are shaped to create lift.",
            "How do airplanes fly?");
        String flight="Air flows over the wings, which are shaped to create lift. "
            +"This shape is called an airfoil. Uses : Transport : Aircraft carry passengers. "
            +"War : Aircraft bombed Libya in 1911.";
        String flightExcerpt=ResearchEvidence.excerpt(flight,"How do airplanes fly?",1100);
        if(flightExcerpt.contains("Transport") || flightExcerpt.contains("Libya"))
            throw new AssertionError("Unrelated aircraft uses included in mechanism: "+flightExcerpt);
        assertAccepted("Western Roman Empire","The Empire had weak leadership, which caused instability and helped invasions.",
            "Why did the Roman Empire fall?");
        assertAccepted("Financial crisis of 2007–2008",
            "The factors that led to the crisis were reported earlier. Background and causes: risky lending spread.",
            "What caused the 2008 financial crisis?");
        assertAccepted("Kyoto","A train runs from Tokyo to Kyoto.",
            "How can I travel from Tokyo to Kyoto by train?");
        System.out.println("ResearchEvidence cases passed");
    }

    private static void assertRejected(String title,String text,String question) {
        if(ResearchEvidence.sufficientlyCovered(title,text,question))
            throw new AssertionError("Unrelated evidence accepted: "+title+" / "+question);
    }
    private static void assertAccepted(String title,String text,String question) {
        if(!ResearchEvidence.sufficientlyCovered(title,text,question))
            throw new AssertionError("Relevant evidence missed: "+title+" / "+question);
    }
}
