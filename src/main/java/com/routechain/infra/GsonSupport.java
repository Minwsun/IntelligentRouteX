package com.routechain.infra;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.Instant;

/**
 * Shared Gson factory for module-safe serialization.
 */
public final class GsonSupport {
    private static final JsonSerializer<Instant> INSTANT_SERIALIZER =
            (src, typeOfSrc, context) -> new JsonPrimitive(src.toString());
    private static final JsonDeserializer<Instant> INSTANT_DESERIALIZER =
            (json, typeOfT, context) -> Instant.parse(json.getAsString());

    private GsonSupport() {}

    public static Gson compact() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, INSTANT_SERIALIZER)
                .registerTypeAdapter(Instant.class, INSTANT_DESERIALIZER)
                .create();
    }

    public static Gson pretty() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, INSTANT_SERIALIZER)
                .registerTypeAdapter(Instant.class, INSTANT_DESERIALIZER)
                .setPrettyPrinting()
                .create();
    }
}
