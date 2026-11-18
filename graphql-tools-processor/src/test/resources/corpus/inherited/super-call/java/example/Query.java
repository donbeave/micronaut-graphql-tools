package example;

public class Query extends BaseQuery {

    @Override
    public String hello() {
        return super.hello() + " derived";
    }

}
