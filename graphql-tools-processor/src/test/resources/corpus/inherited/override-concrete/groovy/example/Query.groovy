package example

class Query extends BaseQuery {

    @Override
    String hello() {
        return 'derived'
    }

}
