package example

import groovy.transform.CompileStatic
import placed.Placed

@CompileStatic
class Main {
    static String greeting() {
        Placed.value()
    }
}
