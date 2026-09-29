package org.offlineatlas;

public final class EvidenceFallbackCases {
    public static void main(String[] args) {
        String plane="When the aircraft travels forwards, air flows over the wings, which are shaped to create lift. "
            +"This shape is called an airfoil and is shaped like a bird's wing. Uses : Transport : Passengers travel.";
        String fallback=EvidenceFallback.fromExcerpt(plane);
        if(!fallback.contains("create lift [1].") || !fallback.contains("airfoil")
            || fallback.contains("Passengers")) throw new AssertionError(fallback);
        AnswerReview reviewed=AnswerReview.check(fallback,"[1] Airplane. "+plane);
        if(!reviewed.accepted()) throw new AssertionError(reviewed.reason);
        String soviet="Many factors and events combined and finally they resulted in the dissolution of the Soviet Union. "
            +"Under glasnost, the government lost control over the media. "
            +"A free media brought poor housing and corruption to public notice.";
        fallback=EvidenceFallback.fromExcerpt(soviet);
        if(!fallback.contains("dissolution of the Soviet Union [1].")
            || !fallback.contains("poor housing and corruption")) throw new AssertionError(fallback);
        if(!AnswerReview.check(fallback,"[1] Soviet Union. "+soviet).accepted())
            throw new AssertionError("Exact local sentences rejected");
    }
}
