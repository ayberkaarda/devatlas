package dev.bytelore.server.sync.dto;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * One lesson's local state as a client holds it.
 *
 * <p>{@code completedAt} is nullable and the null is meaningful: it records "this lesson was
 * un-completed", a real action a person takes, which has to survive the round trip as such rather
 * than being read as an absence of data. There is deliberately no {@code userId} here -- the batch
 * is scoped to the authenticated caller and nothing in the payload can widen that.
 *
 * @param lessonId the lesson this state is about
 * @param completedAt when it was completed, or null for an explicit un-completion
 * @param clientUpdatedAt the client's own clock at the moment it recorded the change; untrusted
 *     input, clamped rather than rejected when implausibly far ahead of server time
 */
@JsonDeserialize(using = ProgressSyncItem.Deserializer.class)
public record ProgressSyncItem(
    @NotNull UUID lessonId, Instant completedAt, @NotNull Instant clientUpdatedAt) {

  /**
   * Reads an item from the JSON object itself rather than letting the binder map properties onto
   * the record's components.
   *
   * <p>The whole reason is one distinction that ordinary binding destroys: {@code completed_at} is
   * <strong>required as a key and nullable as a value</strong>. Bound normally, an object with no
   * {@code completed_at} key and an object with {@code "completed_at": null} both arrive as a null
   * component, and the two mean opposite things. The second is a person un-completing a lesson; the
   * first is a malformed item, and accepting it as the second would silently erase a completion the
   * person still has, with nothing anywhere reporting that it happened. Reading the tree makes the
   * question answerable -- the key is either present or it is not.
   *
   * <p>The property names are spelled out here rather than derived from the global snake_case
   * naming strategy on purpose: they are the names the API contract states for this payload, and
   * pinning them locally is what stops a change of global convention from silently changing the
   * shape of a documented request.
   */
  static final class Deserializer extends ValueDeserializer<ProgressSyncItem> {

    private static final String LESSON_ID = "lesson_id";
    private static final String COMPLETED_AT = "completed_at";
    private static final String CLIENT_UPDATED_AT = "client_updated_at";

    @Override
    public ProgressSyncItem deserialize(JsonParser parser, DeserializationContext context) {
      JsonNode node = context.readTree(parser);
      if (!node.isObject()) {
        throw new ApiException(
            ErrorCode.MALFORMED_REQUEST, "Each entry of 'items' must be a JSON object.");
      }
      if (!node.has(COMPLETED_AT)) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED,
            "Each item must carry a '%s' key; use null to record an un-completion."
                .formatted(COMPLETED_AT));
      }
      // The two required values are read leniently and left null when absent, so that a missing
      // one is reported by Bean Validation as a field error against its own path rather than as a
      // whole-body parse failure that names nothing.
      return new ProgressSyncItem(
          read(context, node, LESSON_ID, UUID.class),
          read(context, node, COMPLETED_AT, Instant.class),
          read(context, node, CLIENT_UPDATED_AT, Instant.class));
    }

    private static <T> T read(
        DeserializationContext context, JsonNode node, String field, Class<T> type) {
      JsonNode value = node.get(field);
      if (value == null || value.isNull()) {
        return null;
      }
      // Routed back through the context so the configured deserializers still apply -- notably the
      // one that refuses a timestamp with no explicit UTC offset.
      return context.readTreeAsValue(value, type);
    }
  }
}
