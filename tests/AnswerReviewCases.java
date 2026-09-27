package org.offlineatlas;

public final class AnswerReviewCases {
    public static void main(String[] args) {
        String evidence="[1] A mountain site in Peru at 2430m.\n[2] A temple in Cambodia.\n";
        assertValid("A site in Peru at 2430m [1]. A temple in Cambodia [2].",evidence);
        assertValid("A site in Peru at 2430m [1]. A temple in Cambodia [2]. An unfinished",evidence);
        assertReviewed("A site in Peru at 2430m [1]. A temple in Cambodia [2]. This is an uncited comparison.",
            evidence,"A site in Peru at 2430m [1]. A temple in Cambodia [2].");
        assertRejected("This is an uncited assertion. A site in Peru at 2430m [1]. A temple in Cambodia [2].",evidence,"uncited sentence before sourced claims");
        assertRejected("A site in Peru at 1200m [1]. A temple in Cambodia [2].",evidence,"number 1200");
        assertRejected("A site from the 15th century is in Peru [1]. A temple in Cambodia [2].",evidence,"date 15th");
        assertRejected("A mountain site is in Peru on a high ridge [1].",evidence,"missing comparison citation [2]");
        assertRejected("A mountain site is in Peru [1]. A temple in Cambodia [3].",evidence,"missing comparison citation [2]");
        assertRejected("A mountain site is in Peru on a high ridge [1]. A temple in Cambodia [2] then",evidence,"missing comparison citation [2]");
        assertValid("A mountain site is in Peru on a high ridge [1].","[1] A mountain site in Peru.");
        AnswerReview weak=AnswerReview.check("Solar uses the sun [1]. Wind uses moving air [2].",
            "[1] Solar uses the sun. [2] Wind uses moving air.",true);
        if (weak.accepted() || !weak.reason.equals("no direct contrast between the two subjects"))
            throw new AssertionError("Unrelated source recitals must not pass as comparison");
        AnswerReview direct=AnswerReview.check("Solar uses the sun [1], whereas wind uses moving air [2].",
            "[1] Solar uses the sun. [2] Wind uses moving air.",true);
        if (!direct.accepted()) throw new AssertionError(direct.reason);
        assertRejected("Whereas [1] solar uses sun energy, [2] wind uses moving air.",
            "[1] Solar uses the sun. [2] Wind uses moving air.","citation before its factual claim");
        assertRejected("Solar uses sun energy [1], whereas wind uses moving air [2]. [1] https://example.invalid/solar.",
            "[1] Solar uses the sun. [2] Wind uses moving air.","source metadata copied into answer");
    }

    private static void assertValid(String raw,String evidence) {
        AnswerReview review=AnswerReview.check(raw,evidence);
        if (!review.accepted()) throw new AssertionError(review.reason+": "+raw);
    }
    private static void assertRejected(String raw,String evidence,String reason) {
        AnswerReview review=AnswerReview.check(raw,evidence);
        if (review.accepted() || !review.reason.startsWith(reason))
            throw new AssertionError("Expected "+reason+", got "+review.reason);
    }
    private static void assertReviewed(String raw,String evidence,String expected) {
        AnswerReview review=AnswerReview.check(raw,evidence);
        if (!review.accepted() || !review.text.equals(expected))
            throw new AssertionError("Expected "+expected+", got "+review.text+": "+review.reason);
    }
}
