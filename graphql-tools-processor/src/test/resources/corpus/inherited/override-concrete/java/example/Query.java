package example;

import jakarta.inject.Singleton;

@Singleton
public class Query extends BaseQuery {

    @Override
    public String hello() {
        return "derived";
    }

}
