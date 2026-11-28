package example

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename", defaultImpl = Other::class)
@JsonSubTypes(
    JsonSubTypes.Type(value = BookHit::class, name = "Book"),
    JsonSubTypes.Type(value = MagazineHit::class, name = "Magazine")
)
sealed interface Hit
