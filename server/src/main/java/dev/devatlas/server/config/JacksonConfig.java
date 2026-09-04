package dev.devatlas.server.config;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * JSON conventions shared by every endpoint.
 *
 * <p>Property naming is snake_case, configured globally rather than with per-field annotations --
 * see {@code spring.jackson.property-naming-strategy}. Doing it once removes a whole class of
 * "which casing did that field use" bug across the clients, and it means a new DTO cannot
 * accidentally opt out.
 *
 * <p>Timestamps are pinned to one exact pattern. This is not cosmetic: timestamps appear inside
 * payloads whose bytes get digested, and a formatter that emits {@code .48Z} on one code path and
 * {@code .480Z} on another produces two digests for one logical value.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

  /**
   * UTC, literal {@code Z}, and exactly three fractional digits -- always present, so a whole
   * second serializes as {@code .000} rather than dropping the fraction. Both of Java's default
   * behaviours here (trailing zeros elided, nanosecond precision emitted) are wrong for this API
   * and have to be overridden explicitly.
   */
  public static final DateTimeFormatter INSTANT_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  @Bean
  JsonMapperBuilderCustomizer devatlasJsonCustomizer() {
    SimpleModule module = new SimpleModule("devatlas-time");
    module.addSerializer(Instant.class, new FixedPrecisionInstantSerializer());
    module.addDeserializer(Instant.class, new OffsetRequiredInstantDeserializer());
    return builder -> builder.addModule(module);
  }

  /** Writes an {@link Instant} in the single pattern this API emits. */
  static final class FixedPrecisionInstantSerializer extends ValueSerializer<Instant> {

    @Override
    public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
      // Truncated rather than rounded, so a value read back is byte-identical to the one any
      // later comparison will use.
      generator.writeString(INSTANT_FORMAT.format(value.truncatedTo(ChronoUnit.MILLIS)));
    }
  }

  /**
   * Reads an {@link Instant} from a string that carries an explicit zone offset -- {@code Z} or a
   * numeric offset, which is converted to UTC.
   *
   * <p>A naive local timestamp with no offset is rejected rather than guessed at. "Guess the zone"
   * is the failure mode that puts a completion three hours in the future on one machine and three
   * hours in the past on another, and no default is defensible for a value that arrives from an
   * unknown device.
   */
  static final class OffsetRequiredInstantDeserializer extends ValueDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) {
      String raw = parser.getString();
      if (raw == null || raw.isBlank()) {
        return null;
      }
      try {
        return OffsetDateTime.parse(raw).toInstant().truncatedTo(ChronoUnit.MILLIS);
      } catch (DateTimeParseException e) {
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED,
            "Timestamp '%s' must be ISO-8601 with an explicit UTC offset.".formatted(raw),
            e);
      }
    }
  }
}
