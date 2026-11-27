package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BookHit.class, name = "Book"),
        @JsonSubTypes.Type(value = MagazineHit.class, name = "Magazine")
})
public sealed interface Hit permits BookHit, MagazineHit {
}
