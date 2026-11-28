package com.example.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import javax.annotation.processing.Generated;

/**
 * The state of a book.
 */
@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable.Serializable
@Serdeable.Deserializable(
        using = Status.Fallback.class
)
public enum Status {

    /**
     * On the shelf.
     */
    OPEN,
    /**
     * @deprecated Use OPEN.
     */
    @Deprecated
    CLOSED,
    /**
     * A value that this client does not know, such as one that the schema added after the code was generated.
     */
    @JsonEnumDefaultValue
    UNKNOWN__;

    /**
     * Reads a name that no constant has as {@link #UNKNOWN__}.
     */
    @Singleton
    static final class Fallback implements Deserializer<Status> {
        @Override
        public Status deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Status> type) throws IOException {
            String name = decoder.decodeString();
            for (Status constant : values()) {
                if (constant.name().equals(name)) {
                    return constant;
                }
            }
            return UNKNOWN__;
        }
    }
}
