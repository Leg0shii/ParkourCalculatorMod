package de.legoshi.parkourcalc.core.save;

import java.util.Collections;
import java.util.List;

public final class SaveBrowseResult {

    public final List<String> folders;
    public final List<SaveInfo> files;

    public SaveBrowseResult(List<String> folders, List<SaveInfo> files) {
        this.folders = folders;
        this.files = files;
    }

    public static SaveBrowseResult empty() {
        return new SaveBrowseResult(Collections.<String>emptyList(), Collections.<SaveInfo>emptyList());
    }
}
