package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Index implements Serializable {
    /** Create an empty index. */
    public Index() {
        _addMap = new TreeMap<>();
        _rmSet = new TreeSet<>();
    }

    /** Save this index in file for future use. */
    public void saveIndex() {
        File newIndex = new File(Main.INDEX);
        Utils.writeObject(newIndex, this);
    }

    public void saveRemoteIndex(String remoteGitPath) {
        File newIndex = new File(remoteGitPath + "/index");
        Utils.writeObject(newIndex, this);
    }

    /** Return my addMap. */
    public Map<String, String> getAddMap() {
        return _addMap;
    }

    /** Return my rmMap. */
    public Set<String> getRmSet() {
        return _rmSet;
    }

    /**
     * Staging area for addition.
     * Mapping: filename -> blob uid.
     */
    private Map<String, String> _addMap;

    /** Staging area for removal. */
    private Set<String> _rmSet;
}
