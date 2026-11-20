package example;

import jakarta.inject.Singleton;

@Singleton
public class Query extends BaseQuery {

    public String hello(String suffix) {
        return "derived " + suffix;
    }

}
