package ru.hgd.sdlc.shared.kernel;

import java.util.function.Function;

/**
 * A Result type for functional error handling.
 * @param <T> the success value type
 * @param <E> the error type
 */
public sealed interface Result<T, E> permits Result.Success, Result.Failure {

    boolean isSuccess();
    boolean isFailure();
    T getValue();
    E getError();

    <U> Result<U, E> map(Function<T, U> mapper);
    <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper);

    record Success<T, E>(T value) implements Result<T, E> {
        @Override
        public boolean isSuccess() { return true; }
        @Override
        public boolean isFailure() { return false; }
        @Override
        public T getValue() { return value; }
        @Override
        public E getError() { return null; }

        @Override
        public <U> Result<U, E> map(Function<T, U> mapper) {
            return new Success<>(mapper.apply(value));
        }

        @Override
        public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
            return mapper.apply(value);
        }
    }

    record Failure<T, E>(E error) implements Result<T, E> {
        @Override
        public boolean isSuccess() { return false; }
        @Override
        public boolean isFailure() { return true; }
        @Override
        public T getValue() { return null; }
        @Override
        public E getError() { return error; }

        @Override
        public <U> Result<U, E> map(Function<T, U> mapper) {
            return new Failure<>(error);
        }

        @Override
        public <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
            return new Failure<>(error);
        }
    }

    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }
}
