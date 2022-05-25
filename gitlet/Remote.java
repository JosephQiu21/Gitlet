package gitlet;

import java.io.File;
import java.io.Serializable;

/**
 * Represents a remote.
 * Stores login info and directory path.
 * @author Haochen Qiu
 */
public class Remote implements Serializable {
    public Remote(String remoteName, String remoteGitPath) {
        _remoteName = remoteName;
        _remoteGitPath = remoteGitPath;
    }

    /** Save remote to current repository. */
    public void saveRemote() {
        File remote = new File(Main.REMOTES + "/" + getRemoteName());
        Utils.writeObject(remote, this);
    }

    /** Return remote name. */
    public String getRemoteName() {
        return _remoteName;
    }

    /** Return path of remote repository. */
    public String getRemoteGitPath() {
        return _remoteGitPath;
    }

    /** Return true if the remote repository exists. */
    public boolean remoteGitExist() {
        File remoteGit = new File(getRemoteGitPath());
        return remoteGit.exists();
    }

    /** Remote name. */
    private String _remoteName;

    /** Path of the remote repository. */
    private String _remoteGitPath;
}
