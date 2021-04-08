package draylar.rose.api;

import org.jetbrains.annotations.Nullable;

public class Result<T> {

    private final boolean passed;
    private final T result;

    private Result(boolean passed, T result) {
        this.passed = passed;
        this.result = result;
    }

    public static <A> Result<A> fail() {
        return new Result<>(false, null);
    }

    public static <A> Result<A> pass(A value) {
        return new Result<>(true, value);
    }

    /**
     * @return {@code true} if this {@link Result} is valid (operation succeeded, result is valid, etc.), otherwise {@code false}
     */
    public boolean passed() {
        return passed;
    }

    /**
     * @return the response of this {@link Result}. If {@link Result#passed()} is false, this value will be {@code null}.
     */
    @Nullable
    public T get() {
        return result;
    }
}
