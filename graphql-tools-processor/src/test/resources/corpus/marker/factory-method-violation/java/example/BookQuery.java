package example;

public class BookQuery implements BookQueryContract {

    @Override
    public Book book(String id, String extra) {
        return new Book();
    }

    public String describe() {
        return "not a field";
    }

}
