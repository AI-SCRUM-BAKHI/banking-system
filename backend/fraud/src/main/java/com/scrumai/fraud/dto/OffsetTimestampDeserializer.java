package com.scrumai.fraud.dto;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class OffsetTimestampDeserializer extends ValueDeserializer<OffsetDateTime> {
  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      return context.reportInputMismatch(
          OffsetDateTime.class, "Timestamp must be an ISO-8601 string with an explicit UTC offset");
    }
    try {
      return OffsetDateTime.parse(parser.getString());
    } catch (DateTimeParseException exception) {
      return context.reportInputMismatch(
          OffsetDateTime.class, "Timestamp must be an ISO-8601 string with an explicit UTC offset");
    }
  }
}
