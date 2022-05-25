package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.TreeMap;
import java.util.Map;

/**
 * Represents a commit.
 * @author Haochen Qiu
 */
public class Commit implements Serializable {

    public Commit() {
        _logMessage = "initial commit";
        _timeStamp = new Timestamp(0);
        _fileMap = new TreeMap<>();
    }

    public Commit(String commitID, String log) {
        _logMessage = log;
        _timeStamp = new Timestamp(System.currentTimeMillis());
        _parent = commitID;
        Commit parentCommit = Main.getCommit(commitID);
        _fileMap = parentCommit.getFileMap();
    }

    public Commit(String commitID, String mergeCommitID, String log) {
        _logMessage = log;
        _timeStamp = new Timestamp(System.currentTimeMillis());
        _parent = commitID;
        _parent2 = mergeCommitID;
        Commit parentCommit = Main.getCommit(commitID);
        _fileMap = parentCommit.getFileMap();
    }

    /**
     * Save a commit to `.gitlet/commits` for future use.
     * Use SHA-1 value as the filename. */
    public void saveCommit() {
        String uid = Utils.sha1(Utils.serialize(this));
        File newCommit = new File(Main.COMMITS + "/" + uid);
        Utils.writeObject(newCommit, this);
    }

    /**
     * Save a commit to remote.
     * @param remoteGitPath remote git path
     */
    public void saveCommitRemote(String remoteGitPath) {
        String uid = Utils.sha1(Utils.serialize(this));
        File newCommit = new File(remoteGitPath + "/commits/" + uid);
        Utils.writeObject(newCommit, this);
    }

    /**
     * Set branch as my uid.
     * @param branchName name of the branch
     */
    public void setBranchAsMe(String branchName) {
        File branch = new File(Main.REFS + "/" + branchName);
        String[] remoteBranchName = branchName.split("/");
        if (remoteBranchName.length == 2) {
            String remoteName = remoteBranchName[0];
            File remoteFolder = new File(Main.REFS + "/" + remoteName);
            remoteFolder.mkdir();
        }
        if (!branch.exists()) {
            try {
                branch.createNewFile();
            } catch (IOException excp) {
                throw new IllegalArgumentException(excp.getMessage());
            }
        }
        String uid = Utils.sha1(Utils.serialize(this));
        Utils.writeContents(branch, uid);
    }

    /**
     * Set remote branch as me.
     * @param branchName branch name
     * @param remoteGitPath remote git path
     */
    public void setRemoteBranchAsMe(String branchName, String remoteGitPath) {
        File branch = new File(remoteGitPath + "/refs/" + branchName);
        if (!branch.exists()) {
            try {
                branch.createNewFile();
            } catch (IOException excp) {
                throw new IllegalArgumentException(excp.getMessage());
            }
        }
        String uid = Utils.sha1(Utils.serialize(this));
        Utils.writeContents(branch, uid);
    }

    /** Return my log message. */
    public String getLogMessage() {
        return _logMessage;
    }

    /** Return my time stamp. */
    public Timestamp getTimestamp() {
        return _timeStamp;
    }

    /** Return the uid of my parent. */
    public String getParentID() {
        return _parent;
    }

    /** Return the uid of my second parent. */
    public String getParent2ID() {
        return _parent2;
    }

    /** Return my file mapping. */
    public Map<String, String> getFileMap() {
        return _fileMap;
    }

    /** Log Message. */
    private String _logMessage;
    /** Timestamp. */
    private Timestamp _timeStamp;
    /** Parent reference. */
    private String _parent;
    /** (For merge) a second parent reference. */
    private String _parent2;

    /**
     * Mapping of file name to blobID.
     * A simplification of file tree directory.
     */
    private Map<String, String> _fileMap;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!obj.getClass().equals(Commit.class)) {
            return false;
        }
        String thisSHA1 = Utils.sha1(Utils.serialize(this));
        String objSHA1 = Utils.sha1(Utils.serialize((Commit) obj));
        return thisSHA1.equals(objSHA1);
    }

    @Override
    public int hashCode() {
        String thisSHA1 = Utils.sha1(Utils.serialize(this));
        return thisSHA1.hashCode();
    }
}
