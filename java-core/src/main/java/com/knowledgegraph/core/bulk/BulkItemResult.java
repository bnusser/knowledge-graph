package com.knowledgegraph.core.bulk;

public record BulkItemResult(int index, String status, String id, String error) {

    public static BulkItemResult created(int index, String id) {
        return new BulkItemResult(index, "created", id, null);
    }

    public static BulkItemResult alreadyPresent(int index, String id) {
        return new BulkItemResult(index, "already_present", id, null);
    }

    public static BulkItemResult rejected(int index, String error) {
        return new BulkItemResult(index, "rejected", null, error);
    }
}
