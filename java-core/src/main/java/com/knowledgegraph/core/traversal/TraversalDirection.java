package com.knowledgegraph.core.traversal;

import com.knowledgegraph.core.exception.InvalidTraversalRequestException;
import java.util.Locale;

public enum TraversalDirection {
    OUTGOING("outgoing"),
    INCOMING("incoming"),
    BOTH("both");

    private final String apiValue;

    TraversalDirection(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static TraversalDirection parse(String value) {
        if (value == null || value.isBlank()) {
            return BOTH;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "outgoing" -> OUTGOING;
            case "incoming" -> INCOMING;
            case "both" -> BOTH;
            default -> throw new InvalidTraversalRequestException(
                    "direction must be one of outgoing, incoming, or both");
        };
    }
}
