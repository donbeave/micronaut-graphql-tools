package example

import jakarta.inject.Singleton

@Singleton
class Query extends BaseQuery {

    String hello(String suffix) {
        return 'derived ' + suffix
    }

}
