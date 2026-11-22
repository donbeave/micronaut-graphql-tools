package example

class BookQuery implements BookQueryContract {

    @Override
    Book book(String id, String extra) {
        return new Book()
    }

    String describe() {
        return 'not a field'
    }

}
