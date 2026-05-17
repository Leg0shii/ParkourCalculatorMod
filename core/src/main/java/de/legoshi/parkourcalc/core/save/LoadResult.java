package de.legoshi.parkourcalc.core.save;

public final class LoadResult {

    public final boolean ok;
    public final SaveFile file;
    public final String error;

    private LoadResult(boolean ok, SaveFile file, String error) {
        this.ok = ok;
        this.file = file;
        this.error = error;
    }

    public static LoadResult success(SaveFile file) {
        return new LoadResult(true, file, null);
    }

    public static LoadResult failure(String error) {
        return new LoadResult(false, null, error);
    }
}
