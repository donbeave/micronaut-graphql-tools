package example;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class BoxedAndBagged implements Boxed, Bagged {

    @Override
    public String getLabel() {
        return "both";
    }

}
