package example

import jakarta.inject.Singleton

@Singleton
class Query extends BaseQuery {

    @Override
    String hello() {
        return super.hello() + ' derived'
    }

}
