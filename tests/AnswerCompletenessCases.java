package org.offlineatlas;

public final class AnswerCompletenessCases {
    public static void main(String[] args) {
        String question="How do airplanes fly?";
        String evidence="[1] Airplane. It moves by thrust from a jet engine or propeller. "
            +"Air flows over its wings, shaped to create lift.\n"
            +"[2] Lift (force). A wing deflects air downward, and the reaction pushes it up.";
        String earlierPhoneAnswer="When the aircraft travels forwards, air flows over the wings, "
            +"which are shaped like an airfoil to create lift [1].";
        if(AnswerCompleteness.missing(question,earlierPhoneAnswer,evidence).isEmpty())
            throw new AssertionError("Incomplete phone answer passed");
        String complete="A jet engine or propeller provides thrust that moves the airplane forward [1]. "
            +"As its wing deflects air downward, the reaction pushes the wing upward, producing lift [2].";
        if(!AnswerReview.check(complete,evidence).accepted()
            || !AnswerCompleteness.missing(question,complete,evidence).isEmpty())
            throw new AssertionError("Sourced two-step explanation rejected");
        if(!AnswerCompleteness.sovietQuestion("Why did the Soviet Union collapse?"))
            throw new AssertionError("Known cause question not recognized");
        String oldSoviet="Under glasnost, the Communist Party lost control over the media [1]. "
            +"Free media exposed social and economic problems [1].";
        String sovietEvidence="[1] History. Under glasnost the party lost media control. "
            +"[2] Dissolution. Republics left the union; the economy struggled.";
        if(AnswerCompleteness.missing("Why did the Soviet Union collapse?",oldSoviet,sovietEvidence).isEmpty())
            throw new AssertionError("Incomplete Soviet answer passed");
        String liftRaw="The force of lift is the upward force that keeps an aircraft in the air. "
            +"Lift has several explanations. The simplest explanation is that the wing deflects air downward, "
            +"and the reaction pushes the wing up.";
        String lift=SourceWindows.airplaneLift(liftRaw);
        if(!lift.contains("upward force") || !lift.contains("deflects air downward"))
            throw new AssertionError("Relevant lift mechanism omitted: "+lift);
        String sovietRaw="The dissolution of the Soviet Union was a large event. "
            +"It started when Estonia declared independence. After that, many Soviet republics followed "
            +"and eventually all state republics left the Union. "
            +"The Soviet Union at the time was struggling with the economy and protests.";
        String factors=SourceWindows.sovietFactors(sovietRaw);
        if(!factors.contains("republics followed") || !factors.contains("struggling with the economy"))
            throw new AssertionError("Two historical factors omitted: "+factors);
        if(!EvidenceFallback.fromExcerpt("A wing deflects air downward and the reaction pushes it up.",2).contains("[2]."))
            throw new AssertionError("Second source citation lost");
    }
}
