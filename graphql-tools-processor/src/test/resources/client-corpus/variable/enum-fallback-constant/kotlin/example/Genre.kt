package example

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue

enum class Genre {
    FICTION, SCIENCE, @JsonEnumDefaultValue UNKNOWN
}
