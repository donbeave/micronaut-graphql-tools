package example;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum Genre {
    FICTION, SCIENCE, @JsonEnumDefaultValue UNKNOWN
}
