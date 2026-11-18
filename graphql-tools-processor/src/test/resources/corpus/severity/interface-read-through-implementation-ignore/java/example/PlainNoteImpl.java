package example;

import io.micronaut.graphql.tools.annotation.GraphQLType;

@GraphQLType(PlainNote.class)
public class PlainNoteImpl implements PlainNote {

    public String getText() {
        return "written";
    }

}
