package example

class Query extends BaseQuery {

    String hello(String suffix) {
        return 'derived ' + suffix
    }

}
