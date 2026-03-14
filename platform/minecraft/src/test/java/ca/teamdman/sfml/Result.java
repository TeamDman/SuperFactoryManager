package ca.teamdman.sfml;

public interface Result<T, E> {
    @SuppressWarnings("unchecked")
    default T unwrap() {

        if (this instanceof Ok ok) {
            return (T) ok.value();
        } else {
            throw new IllegalStateException("Result is error variant: " + this);
        }
    }

    record Ok<T>(T value) implements Result<T, Void> {
    }

    record Err<E>(E value) implements Result<Void, E> {
    }

}
