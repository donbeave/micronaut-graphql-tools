package example;

import example.model.Marked;
import io.micronaut.core.annotation.Introspected;

@Introspected(packages = "example.model", includedAnnotations = Marked.class)
public class Introspections {
}
