package com.ticketflow.waitlist.entity;

public enum WaitlistStatus {
    /** In the queue, waiting for a seat to free up. */
    ACTIVE,
    /** Currently holding a time-limited offer — temporarily out of the queue. */
    OFFERED,
    /** Accepted an offer and completed (or is completing) a booking. */
    CONVERTED,
    /** Let an offer lapse; moved to the back is NOT automatic — see WaitlistService. */
    EXPIRED,
    /** Left the queue voluntarily. */
    CANCELLED
}
