package example.model;

@Marked
public class Author {

    private final String name;

    public Author(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
