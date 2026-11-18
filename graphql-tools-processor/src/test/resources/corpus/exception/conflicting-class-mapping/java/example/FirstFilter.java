package example;

import io.micronaut.graphql.tools.annotation.GraphQLInput;

@GraphQLInput
public class FirstFilter {

    private String text;
    private String extra;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

}
