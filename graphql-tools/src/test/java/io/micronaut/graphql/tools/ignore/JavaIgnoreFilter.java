package io.micronaut.graphql.tools.ignore;

import io.micronaut.graphql.tools.annotation.GraphQLIgnore;
import io.micronaut.graphql.tools.annotation.GraphQLInput;

/**
 * An input class with a settable property that the input type does not define, marked {@code @GraphQLIgnore}.
 */
@GraphQLInput
public class JavaIgnoreFilter {

    private String text;
    private String note;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @GraphQLIgnore
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

}
