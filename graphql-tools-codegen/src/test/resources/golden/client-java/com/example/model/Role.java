package com.example.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import javax.annotation.processing.Generated;

@Generated("io.micronaut.graphql.tools.codegen")
@Serdeable.Serializable
@Serdeable.Deserializable(
        using = Role.Fallback.class
)
public enum Role {

    ADMIN,
    MEMBER,
    /**
     * A value that this client does not know, such as one that the schema added after the code was generated.
     */
    @JsonEnumDefaultValue
    UNKNOWN__;

    /**
     * Reads a name that no constant has as {@link #UNKNOWN__}.
     */
    @Singleton
    static final class Fallback implements Deserializer<Role> {
        @Override
        public Role deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Role> type) throws IOException {
            String name = decoder.decodeString();
            for (Role constant : values()) {
                if (constant.name().equals(name)) {
                    return constant;
                }
            }
            return UNKNOWN__;
        }
    }
}
