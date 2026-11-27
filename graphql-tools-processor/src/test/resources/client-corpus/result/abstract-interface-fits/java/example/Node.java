package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename", defaultImpl = OtherNode.class)
@JsonSubTypes({@JsonSubTypes.Type(value = BookNode.class, name = "Book")})
public sealed interface Node permits BookNode, OtherNode {
}
