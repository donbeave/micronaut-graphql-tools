package example;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class OnlyBoxed implements Boxed {

    @Override
    public String getLabel() {
        return "boxed";
    }

}
