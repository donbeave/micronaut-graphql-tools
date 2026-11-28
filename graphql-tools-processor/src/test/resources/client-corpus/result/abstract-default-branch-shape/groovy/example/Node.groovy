package example

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename", defaultImpl = OtherNode)
@JsonSubTypes([@JsonSubTypes.Type(value = BookNode, name = "Book")])
interface Node {
}
