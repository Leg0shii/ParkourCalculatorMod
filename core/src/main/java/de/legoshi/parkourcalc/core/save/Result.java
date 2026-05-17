package de.legoshi.parkourcalc.core.save;

public final class Result<T> {

    public final boolean ok;
    public final T value;
    public final String error;

    private Result(boolean ok, T value, String error) {
        this.ok = ok;
        this.value = value;
        this.error = error;
    }

    public static <T> Result<T> success(T value) {
        return new Result<T>(true, value, null);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<T>(false, null, error);
    }
}
