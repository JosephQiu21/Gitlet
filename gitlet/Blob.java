package gitlet;

import java.io.File;
import java.io.Serializable;

/**
 * Represents a blob in Gitlet.
 * @author Haochen Qiu
 */
public class Blob implements Serializable {

    /** Create a blob with CONTENT. */
    public Blob(String content) {
        _content = content;
    }

    /** Save this blob to file for future use. */
    public void saveBlob() {
        String uid = Utils.sha1(_content);
        File newBlob = new File(Main.BLOBS + "/" + uid);
        Utils.writeObject(newBlob, this);
    }

    /** Save this blob to remote. */
    public void saveBlobRemote(String remoteGitPath) {
        String uid = Utils.sha1(_content);
        File newBlob = new File(remoteGitPath + "/blobs/" + uid);
        Utils.writeObject(newBlob, this);
    }


    /** Return my content. */
    public String getContent() {
        return _content;
    }
    /** Content of the blob. */
    private String _content;
}
