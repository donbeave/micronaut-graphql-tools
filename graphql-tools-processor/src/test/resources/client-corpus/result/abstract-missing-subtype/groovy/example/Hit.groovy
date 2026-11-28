package example

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename", defaultImpl = Other)
@JsonSubTypes([
    @JsonSubTypes.Type(value = BookHit, name = "Book")
])
interface Hit {
}
