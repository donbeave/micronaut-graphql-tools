package example;

import java.util.List;

public abstract class BaseQuery<T, I> {

    public T book(I id) {
        return null;
    }

    public List<T> books(Integer limit) {
        return List.of();
    }

}
