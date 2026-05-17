package de.legoshi.parkourcalc.core.save;

public final class SaveResult {

    public final boolean ok;
    public final String name;
    public final String error;

    private SaveResult(boolean ok, String name, String error) {
        this.ok = ok;
        this.name = name;
        this.error = error;
    }

    public static SaveResult success(String name) {
        return new SaveResult(true, name, null);
    }

    public static SaveResult failure(String error) {
        return new SaveResult(false, null, error);
    }
}
