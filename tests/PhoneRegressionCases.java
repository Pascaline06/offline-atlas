package org.offlineatlas;

/** Reproduces the S10+ airplane response through the source and answer path. */
public final class PhoneRegressionCases {
    public static void main(String[] args) {
        String excerpt="When the aircraft travels forwards, air flows over the wings, which are shaped to create lift. "
            +"This shape is called an airfoil and is shaped like a bird's wing.";
        String cited=EvidenceFallback.fromExcerpt(excerpt);
        if(!cited.equals("When the aircraft travels forwards, air flows over the wings, which are shaped to create lift [1]. "
            +"This shape is called an airfoil and is shaped like a bird's wing [1]."))
            throw new AssertionError("Immediate cited passage: "+cited);
        String raw="When the aircraft travels forwards, air flows over the wings, which are shaped like an airfoil to create lift. [1].";
        AnswerReview review=AnswerReview.check(raw,"[1] Airplane. "+excerpt);
        if(!review.accepted() || !review.text.equals("When the aircraft travels forwards, air flows over the wings, which are shaped like an airfoil to create lift [1]."))
            throw new AssertionError("Phone model draft: "+review.reason+" / "+review.text);
    }
}
