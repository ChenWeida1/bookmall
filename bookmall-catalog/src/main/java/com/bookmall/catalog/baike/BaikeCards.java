package com.bookmall.catalog.baike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 把百科名片 JSON 收成 {@link BaikeCard}。解析失败视为没有词条。
 */
public final class BaikeCards {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BaikeCards() {
    }

    /** @return 没有编号或没有书名时为 null */
    public static BaikeCard parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return parse(MAPPER.readTree(json));
        } catch (Exception ex) {
            return null;
        }
    }

    /** @return 没有编号或没有书名时为 null */
    public static BaikeCard parse(JsonNode root) {
        if (root == null || root.isMissingNode() || root.hasNonNull("errno")) {
            return null;
        }
        long lemmaId = root.path("newLemmaId").asLong(0);
        String title = text(root, "title");
        if (lemmaId <= 0 || title == null) {
            return null;
        }
        return new BaikeCard(
                lemmaId,
                title,
                author(root.path("card")),
                text(root, "abstract"),
                catalog(root.path("catalog")),
                clip(text(root, "url"), 500),
                clip(text(root, "image"), 255),
                text(root, "desc"));
    }

    private static String author(JsonNode card) {
        if (!card.isArray()) {
            return null;
        }
        for (JsonNode item : card) {
            String name = item.path("name").asText("");
            if (!name.contains("作者") && !name.contains("著者")) {
                continue;
            }
            JsonNode value = item.path("value");
            if (value.isArray() && !value.isEmpty() && !value.get(0).asText("").isBlank()) {
                return clip(value.get(0).asText().trim(), 64);
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return clip(value.asText().trim(), 64);
            }
        }
        return null;
    }

    private static String catalog(JsonNode node) {
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (!node.isArray()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                lines.add(item.asText().trim());
            } else if (item.hasNonNull("name")) {
                lines.add(item.get("name").asText().trim());
            } else if (item.hasNonNull("title")) {
                lines.add(item.get("title").asText().trim());
            }
        }
        if (lines.isEmpty()) {
            return null;
        }
        return String.join("\n", lines);
    }

    private static String text(JsonNode root, String field) {
        return blankToNull(root.path(field).asText(""));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return null;
    }
}
