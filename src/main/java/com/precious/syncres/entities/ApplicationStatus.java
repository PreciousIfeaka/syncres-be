package com.precious.syncres.entities;

import java.util.Collections;
import java.util.Set;

public enum

ApplicationStatus {
    SAVED,
    APPLIED,
    PHONE_SCREEN,
    INTERVIEW,
    FINAL_ROUND,
    OFFER,
    ACCEPTED,
    DECLINED,
    REJECTED,
    WITHDRAWN;

    public Set<ApplicationStatus> allowedTransitions() {
        return switch (this) {
            case SAVED -> Set.of(APPLIED, WITHDRAWN);
            case APPLIED -> Set.of(PHONE_SCREEN, REJECTED, WITHDRAWN);
            case PHONE_SCREEN -> Set.of(INTERVIEW, REJECTED, WITHDRAWN);
            case INTERVIEW -> Set.of(OFFER, FINAL_ROUND, REJECTED, WITHDRAWN);
            case FINAL_ROUND -> Set.of(OFFER, REJECTED, WITHDRAWN);
            case OFFER -> Set.of(ACCEPTED, DECLINED);
            case ACCEPTED, DECLINED, REJECTED, WITHDRAWN -> Collections.emptySet();
        };
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
