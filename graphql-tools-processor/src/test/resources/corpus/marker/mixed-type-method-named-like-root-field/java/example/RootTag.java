package example;

import jakarta.inject.Singleton;

@Singleton
public class RootTag implements RootTagContract {

    @Override
    public String tag() {
        return "root";
    }

}
