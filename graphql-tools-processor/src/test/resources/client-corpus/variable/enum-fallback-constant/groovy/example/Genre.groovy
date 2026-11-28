package example

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

enum Genre {
    FICTION, SCIENCE, @JsonEnumDefaultValue UNKNOWN
}
