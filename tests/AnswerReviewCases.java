package org.offlineatlas;
public final class AnswerReviewCases {
    public static void main(String[] args) {
        String evidence="[1] A mountain site in Peru at 2430m.\n[2] A temple in Cambodia.";
        valid("A site in Peru at 2430m [1]. A temple in Cambodia [2].",evidence);
        invalid("A site in Peru at 1200m [1].",evidence,"number absent");
        invalid("The sensor operates at 24 volts [1].","[1] The sensor operates at 124 volts.","number absent");
        valid("The count is 1000 [1].","[1] The count is 1,000.");
        valid("The value is 1.0 [1].","[1] The value is 1.");
        valid("It happened on the 12th [1].","[1] It happened on day 12.");
        invalid("A site in Peru [3].",evidence,"citation outside");
        invalid("A site in Peru [1]. This is an uncited factual claim.",evidence,"uncited factual");
        invalid("Stable learned knowledge with an invented reference [1].","","invented citation");
        valid("Stable learned knowledge without a citation.","");
        valid("Opening the valve allows water to flow through the pipe [1].",
            "[1] Opening the valve enables water to flow through the pipe.");
        String swapped="[1] The first site was built in 1400.\n[2] The second site was built in 1200.";
        invalid("The first site was built in 1200 [1].",swapped,"number absent");
        if(AnswerReview.check("The mountain site is in Peru [1].",evidence,true).accepted())
            throw new AssertionError("One-sided comparison accepted");
        valid("A mountain site in Peru. [1].","[1] A mountain site in Peru.");
        // This is explicitly only structural review. The model support-check
        // and independent grading, not citation formatting, assess factual support.
        valid("The Earth is made entirely of chocolate [1].","[1] The Earth orbits the Sun.");
        System.out.println("Answer structure cases passed");
    }
    static void valid(String answer,String evidence) {
        AnswerReview r=AnswerReview.check(answer,evidence);
        if(!r.accepted()) throw new AssertionError(r.reason);
    }
    static void invalid(String answer,String evidence,String reason) {
        AnswerReview r=AnswerReview.check(answer,evidence);
        if(r.accepted() || !r.reason.startsWith(reason)) throw new AssertionError(r.reason);
    }
}
