package example;

public record BookQuery(String prefix) implements BookQueryContract {

    @Override
    public Book book(String id) {
        return new Book();
    }

    public String describe() {
        return prefix;
    }

}
