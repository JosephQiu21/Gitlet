package gitlet;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import static java.io.File.separator;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Haochen Qiu
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
        }
        if (Objects.equals(args[0], "init")) {
            init(args);
            return;
        }
        /* Other command: check if gitlet is initialized */
        File gitRepo = new File(".gitlet");
        if (!gitRepo.exists()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }

        switch (args[0]) {
        case "add":
            add(args);
            break;
        case "commit":
            commit(args);
            break;
        case "checkout":
            checkout(args);
            break;
        case "rm":
            rm(args);
            break;
        case "log":
            log(args);
            break;
        case "global-log":
            globalLog(args);
            break;
        case "find":
            find(args);
            break;
        case "status":
            status(args);
            break;
        case "branch":
            branch(args);
            break;
        case "rm-branch":
            rmBranch(args);
            break;
        case "reset":
            reset(args);
            break;
        case "merge":
            merge(args);
            break;
        case "add-remote":
            addRemote(args);
            break;
        case "rm-remote":
            rmRemote(args);
            break;
        case "push":
            push(args);
            break;
        case "fetch":
            fetch(args);
            break;
        case "pull":
            pull(args);
            break;
        default:
            exitWithError("No command with that name exists.");
        }
    }

    /**
     * Creates a new Gitlet VCS in the current directory!
     * This system will automatically start with one commit:
     * a commit that contains no files and has the commit message
     * `initial commit` It will have a single branch: `master`,
     * which initially points to this initial commit, and `master`
     * will be the current branch.
     * @param args Array in format: {init}
     */
    private static void init(String... args) {
        if (args.length != 1) {
            exitWithError("Incorrect operands.");
        }

        /* Check if there is an existing Gitlet VCS in the current directory. */
        File gitRepo = new File(".gitlet");
        if (gitRepo.exists()) {
            exitWithError("A Gitlet version-control system "
                    + "already exists in the current directory.");
        }

        /* Create and initialize `.gitlet` */
        File commits = new File(COMMITS);
        File blobs = new File(BLOBS);
        File refs = new File(REFS);
        File remotes = new File(REMOTES);
        gitRepo.mkdir();
        commits.mkdir();
        blobs.mkdir();
        refs.mkdir();
        remotes.mkdir();
        File head = new File(HEAD);
        File index = new File(INDEX);
        try {
            head.createNewFile();
            index.createNewFile();
        } catch (IOException excp) {
            throw new IllegalArgumentException(excp.getMessage());
        }

        /* Make first commit and serialize */
        Commit firstCommit = new Commit();
        firstCommit.saveCommit();

        /* Initialize staging area */
        clearStagingArea();

        /* Set and save `master` branch */
        firstCommit.setBranchAsMe("master");

        /* Set and save HEAD as `master` */
        String masterPath = REFS + "/master";
        Utils.writeContents(head, masterPath);
    }

    /**
     * Add a copy of the file to the staging area.
     * Staging an already-staged file overwrites the existing entry
     * in the staging area. If the file is identical to the version
     * in the current commit, do not stage it, and remove the staged
     * file if it is already stage.
     * @param args Array in format: {add, filename}
     */
    private static void add(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String filename = args[1];
        File targetFile = new File(filename);
        if (!targetFile.exists()) {
            exitWithError("File does not exist.");
        }

        /* Case: file identical to current commit */
        String targetFileString = Utils.readContentsAsString(targetFile);
        String targetFileSHA1 = Utils.sha1(targetFileString);
        Commit headCommit = getHead();
        Map<String, String> headFileMap = headCommit.getFileMap();
        if (headFileMap.containsKey(filename)
                && headFileMap.get(filename).equals(targetFileSHA1)) {
            /* Remove from staging area for addition */
            Index index = getIndexObject();
            index.getAddMap().remove(filename);
            index.saveIndex();
        } else {
            /* Create blob and stage file for addition */
            stageFileAdd(filename);
        }
        /* Remove from staging area for removal */
        Index index = getIndexObject();
        index.getRmSet().remove(filename);
        index.saveIndex();
    }

    /**
     * Save a snapshot of current tracking files.
     * By default, each commit's snapshot of files will be exactly the same as
     * its parent commit, only update the files that have been staged at the
     * time of commit. A commit will save and start tracking any files that were
     * staged for addition but weren't tracked by its parent. Files tracked in
     * the current commit may be untracked by the new commit as a result of
     * being staged for removal by the `rm` command.
     * @param args Arrays in format: {commit, log}
     */
    private static void commit(String... args) {
        /* For merge: use a format:
        commit [log message] mergeCommitID */
        boolean merge = args.length == 3;

        /* Create a commit object from HEAD */
        String log = args[1];
        if (log.equals("")) {
            exitWithError("Please enter a commit message.");
        }

        Commit newCommit;
        if (merge) {
            String mergeCommitID = args[2];
            newCommit = new Commit(getHeadCommitID(), mergeCommitID, log);
        } else {
            newCommit = new Commit(getHeadCommitID(), log);
        }

        /* Modify file map according to staging area */
        Index index = getIndexObject();
        Map<String, String> addMap = index.getAddMap();
        Set<String> rmSet = index.getRmSet();
        if (addMap.isEmpty() && rmSet.isEmpty()) {
            exitWithError("No changes added to the commit.");
        }
        for (Map.Entry<String, String> entry : addMap.entrySet()) {
            newCommit.getFileMap().put(entry.getKey(), entry.getValue());
        }
        for (String rmFilename : rmSet) {
            newCommit.getFileMap().remove(rmFilename);
        }
        String newCommitID = Utils.sha1(Utils.serialize(newCommit));
        newCommit.saveCommit();

        /* Clear staging area */
        clearStagingArea();

        /* Update active branch */
        File head = new File(HEAD);
        File headBranch = new File(Utils.readContentsAsString(head));
        Utils.writeContents(headBranch, newCommitID);
    }

    /**
     * Unstage a file if it is currently staged for addition.
     * If the file is tracked in current commit, stage it for removal
     * and remove the file from current working directory if the user
     * has not already done so.
     * @param args Arrays in format: {rm, filename}
     */
    private static void rm(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String filename = args[1];
        Commit headCommit = getHead();

        /* If unstaged and untracked, no need to remove the file */
        if (!isStagedForAdd(filename) && !isTracked(filename, headCommit)) {
            exitWithError("No reason to remove the file.");
        }

        /* Get index object and unstage the file */
        Index index = getIndexObject();
        Map<String, String> addMap = index.getAddMap();
        Set<String> rmSet = index.getRmSet();
        addMap.remove(filename);
        rmSet.remove(filename);
        index.saveIndex();

        /* If tracked in current commit, stage for removal */
        if (isTracked(filename, headCommit)) {
            index.getRmSet().add(filename);
            index.saveIndex();
        }

        /* If tracked but not deleted, remove from cwd */
        File targetFile = new File(filename);
        if (isTracked(filename, headCommit) && targetFile.exists()) {
            targetFile.delete();
        }
    }

    /**
     * Starting at the current head commit, display information about
     * each commit backwards along the commit tree until the initial commit,
     * following the first parent commit links. For every node in this
     * history, the information it should display is the commit id,
     * the time the commit was made, and the commit message.
     * @param args Arrays in format: {log}
     */
    private static void log(String... args) {
        if (args.length != 1) {
            exitWithError("Incorrect operands.");
        }
        Commit commitToDisplay = getHead();
        SimpleDateFormat myFormatter =
                new SimpleDateFormat("E MMM d HH:mm:ss y Z");
        myFormatter.setTimeZone(TimeZone.getTimeZone("GMT-8"));
        Boolean display2nd = false;
        String commit2ID = null;
        Timestamp mergeTime = null;
        while (commitToDisplay != null) {
            /* Print log message */
            printLog(commitToDisplay, myFormatter);

            /* Display log of second parent */
            if (display2nd) {
                Commit commit2 = getCommit(commit2ID);
                String timeStamp2 =
                        myFormatter.format(commit2.getTimestamp().getTime());
                String mergeTimeStamp = myFormatter.format(mergeTime.getTime());
                String uid = Utils.sha1(Utils.serialize(commitToDisplay));
                System.out.println("=== commit " + commit2ID + "Merge: "
                        + uid.substring(0, 7) + " " + commit2ID.substring(0, 7)
                        + " " + "Date: " + mergeTimeStamp
                        + " Merged development into master.");
                display2nd = false;
            }

            if (commitToDisplay.getParent2ID() != null) {
                display2nd = true;
                commit2ID = commitToDisplay.getParent2ID();
                mergeTime = commitToDisplay.getTimestamp();
            }

            commitToDisplay = getCommit(commitToDisplay.getParentID());
        }
    }

    /**
     * Display information about all logs ever made.
     * The order of the commits does not matter.
     * @param args Arrays in format: {global-log}
     */
    private static void globalLog(String... args) {
        if (args.length != 1) {
            exitWithError("Incorrect operands");
        }
        List<String> commitFilenames = Utils.plainFilenamesIn(COMMITS);
        SimpleDateFormat myFormatter =
                new SimpleDateFormat("E MMM d HH:mm:ss y Z");
        myFormatter.setTimeZone(TimeZone.getTimeZone("GMT-8"));
        for (String commitID : commitFilenames) {
            Commit commitToDisplay = getCommit(commitID);
            printLog(commitToDisplay, myFormatter);
        }
    }

    /**
     * Prints out all the ids of all commits that have the given commit
     * message. If there are multiple such commits, print the ids on
     * separate lines.
     * @param args Arrays in format: {find, commitMessage}
     */
    private static void find(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String logMessage = args[1];
        List<String> commitFilenames = Utils.plainFilenamesIn(COMMITS);
        boolean found = false;
        for (String commitID : commitFilenames) {
            Commit commitToCheck = getCommit(commitID);
            if (commitToCheck.getLogMessage().equals(logMessage)) {
                found = true;
                System.out.println(commitID);
            }
        }
        if (!found) {
            exitWithError("Found no commit with that message.");
        }
    }

    /**
     * Display branches, mark current branch with *.
     * Display what files have been staged for addition or removal.
     * Display files that are modified but not staged.
     * Display untracked files.
     * @param args Array in format: {find}
     */
    private static void status(String... args) {
        if (args.length != 1) {
            exitWithError("Incorrect operands.");
        }

        /* Branches */
        /* Result is collected in List `branchNames` */
        List<String> branchNames = Utils.plainFilenamesIn(REFS);
        branchNames.sort(null);
        String headBranchName = getHeadBranchName();
        int headIndex = branchNames.indexOf(headBranchName);
        branchNames.set(headIndex, "*" + headBranchName);

        /* Staging area */
        /* Result is collected in List `addFiles` and `rmFiles` */
        Index indexObject = getIndexObject();
        Map<String, String> addMap = indexObject.getAddMap();
        Set<String> rmSet = indexObject.getRmSet();
        List<String> addFiles = new ArrayList<>();
        List<String> rmFiles = new ArrayList<>();
        addFiles.addAll(addMap.keySet());
        addFiles.sort(null);
        rmFiles.addAll(rmSet);
        rmFiles.sort(null);

        /* Modifications not staged */
        /* Result is collected in List `unstagedFiles` */
        /* Tracked in the current commit, changed in
         * the working directory, but not staged
         */
        List<String> unstagedModified = new ArrayList<>();
        List<String> unstagedDeleted = new ArrayList<>();
        Commit currentCommit = getHead();
        for (Map.Entry<String, String> entry
                : currentCommit.getFileMap().entrySet()) {
            String filename = entry.getKey();
            File file = new File(filename);
            if (file.exists()) {
                String fileID = Utils.sha1(Utils.readContentsAsString(file));
                if (!fileID.equals(entry.getValue())
                        && !isStagedForAdd(filename)) {
                    unstagedModified.add(filename);
                }
            } else {
                /* Not staged for removal, but tracked in the current commit
                and deleted from the working directory. */
                if (!isStagedForRM(filename)) {
                    unstagedDeleted.add(filename);
                }
            }

        }
        /* Staged for addition, but with different content
         * than in the working directory
         */
        for (Map.Entry<String, String> entry : addMap.entrySet()) {
            String filename = entry.getKey();
            File file = new File(filename);
            if (file.exists()) {
                String fileID = Utils.sha1(Utils.readContentsAsString(file));
                if (!fileID.equals(entry.getValue())) {
                    unstagedModified.add(filename);
                }
            } else {
                /* Staged for addition, but deleted in the working directory */
                unstagedDeleted.add(filename);
            }
        }
        unstagedModified.sort(null);
        unstagedDeleted.sort(null);

        /* Untracked files */
        List<String> untrackedFiles = new ArrayList<>();
        List<String> files =
                Utils.plainFilenamesIn(System.getProperty("user.dir"));
        for (String filename : files) {
            boolean isTracked =
                    currentCommit.getFileMap().containsKey(filename);
            if (!isTracked && !isStagedForAdd(filename)) {
                untrackedFiles.add(filename);
            }
        }
        untrackedFiles.sort(null);

        /* Print status */
        System.out.println("=== Branches ===");
        for (String s : branchNames) {
            System.out.println(s);
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        for (String s : addFiles) {
            System.out.println(s);
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        for (String s : rmFiles) {
            System.out.println(s);
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        for (String s : unstagedModified) {
            System.out.println(s + " (modified)");
        }
        for (String s : unstagedDeleted) {
            System.out.println(s + " (deleted)");
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        for (String s : untrackedFiles) {
            System.out.println(s);
        }
    }

    /**
     * 3 different uses:
     * 1. checkout -- [file name]: Take the version of the file in the
     * head commit, put it in working directory, overwritting the version
     * of file that's already there if there is one.
     * 2. checkout [commit id] -- [file name]: Take the version of the
     * file in the commit, put it in working directory, overwritting
     * the version of file that's already there if there is one.
     * 3. checkout [branch name]: Take all the files in the commit at the
     * head of the given branch, overwritting the version of files that's
     * already there if they exist. Set HEAD as the given branch.
     * Any files that are tracked in the current branch but not in the given
     * branch are deleted. The staging area is cleared, unless the check-out
     * branch is the current branch.
     * @param args Arrays in format described above
     */
    private static void checkout(String... args) {
        if (args.length > 4
                || args.length == 4 && !Objects.equals(args[2], "--")
                || args.length == 3 && !Objects.equals(args[1], "--")) {
            exitWithError("Incorrect operands.");
        }

        if (args[1].equals("--")) {
            checkoutFile(args[2], getHead());
        } else if (args.length == 4) {
            String commitID = args[1];
            String filename = args[3];
            checkoutFile(filename, getCommit(commitID));
        } else {
            String branchName = args[1];
            Commit checkoutCommit = getBranch(branchName, false);
            if (branchName.equals(getHeadBranchName())) {
                exitWithError("No need to checkout the current branch.");
            }
            checkoutCommit(checkoutCommit);
            /* Set HEAD as checkout branch */
            File head = new File(HEAD);
            Utils.writeContents(head, REFS + "/" + branchName);
        }
    }

    /**
     * Creates a new branch with the given name, and points it at
     * the current head node. This command does not immediately
     * switch to the newly created branch. Before you ever call branch,
     * your code should be running with a default branch called "master".
     * @param args Arrays in format: {branch, branchName}
     */
    private static void branch(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String branchName = args[1];
        File newBranch = new File(REFS + "/" + branchName);
        if (newBranch.exists()) {
            exitWithError("A branch with that name already exists.");
        }
        /* Create a new branch */
        try {
            newBranch.createNewFile();
        } catch (IOException excp) {
            throw new IllegalArgumentException(excp.getMessage());
        }
        /* Point it at the current head node */
        Utils.writeContents(newBranch, getHeadCommitID());
    }

    /**
     * Delete the branch with the given name.
     * @param args Array in format: {rm-branch, branchName}
     */
    private static void rmBranch(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String branchName = args[1];
        File branchToDelete = new File(REFS + "/" + branchName);
        if (!branchToDelete.exists()) {
            exitWithError("A branch with that name does not exist.");
        }
        if (branchName.equals(getHeadBranchName())) {
            exitWithError("Cannot remove the current branch.");
        }
        /* Delete the branch */
        branchToDelete.delete();
    }

    /**
     * Checks out all the file tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Moves the current branch's head to that commit node.
     * @param args Arrays in format: {reset, commitID}
     */
    private static void reset(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String commitID = args[1];
        Commit checkoutCommit = getCommit(commitID);
        checkoutCommit(checkoutCommit);
        /* Move the current branch's head to commit node */
        checkoutCommit.setBranchAsMe(getHeadBranchName());
    }

    /**
     * Merges files from the given branch into the current branch.
     * @param args Array in format: {merge, branchName}
     */
    private static void merge(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }

        /* Exit if there are unstaged files */
        Index indexObject = getIndexObject();
        Map<String, String> addMap = indexObject.getAddMap();
        Set<String> rmSet = indexObject.getRmSet();
        if (!addMap.isEmpty() || !rmSet.isEmpty()) {
            exitWithError("You have uncommitted changes.");
        }

        String branchName = args[1];
        String mergeCommitID = getBranchCommitID(branchName, true);
        Commit mergeCommit = getBranch(branchName, true);
        Commit headCommit = getHead();

        /* Exit if trying to merge with self */
        if (branchName.equals(getHeadBranchName())) {
            exitWithError("Cannot merge a branch with itself.");
        }

        Commit splitCommit = getSplitPoint(mergeCommit, headCommit);

        /* If split point is the same commit as the given branch, do nothing */
        if (splitCommit.equals(mergeCommit)) {
            System.out.println("Given branch is an ancestor "
                    + "of the current branch.");
            return;
        }
        /* If split point is the same commit as the current branch,
        checkout to given branch */
        if (splitCommit.equals(headCommit)) {
            checkoutCommit(mergeCommit);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        Map<String, String> headFileMap = headCommit.getFileMap();
        Map<String, String> mergeFileMap = mergeCommit.getFileMap();
        Map<String, String> splitFileMap = splitCommit.getFileMap();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(headFileMap.keySet());
        allFiles.addAll(splitFileMap.keySet());
        allFiles.addAll(mergeFileMap.keySet());
        boolean isConflict = false;
        for (String filename : allFiles) {
            /* Files modified since split point in given branch but
             * not modified in current branch should be checkout to
             * given branch, and are staged
             */
            /* Files that were not present at the split point and
             * are present only in the given branch should be checked
             * out and staged.
             */
            boolean onlyModifiedInMerge =
                    isTracked(filename, splitCommit)
                            && isTracked(filename, mergeCommit)
                            && isTracked(filename, headCommit)
                            && isModified(filename, mergeCommit, splitCommit)
                            && !isModified(filename, headCommit, splitCommit);
            boolean onlyTrackedByMerge =
                    !isTracked(filename, splitCommit)
                            && !isTracked(filename, headCommit)
                            && isTracked(filename, mergeCommit);
            boolean toCheckout = onlyModifiedInMerge || onlyTrackedByMerge;
            if (toCheckout) {
                checkoutFile(filename, mergeCommit);
                stageFileAdd(filename);
            }

            /* Files present at the split point, unmodified in the
            current branch, and absent in the given branch should
            be removed (and untracked). */
            boolean onlyMissingInMerge =
                    isTracked(filename, splitCommit)
                            && isTracked(filename, headCommit)
                            && !isTracked(filename, mergeCommit)
                            && !isModified(filename, headCommit, splitCommit);
            if (onlyMissingInMerge) {
                /* Untrack */
                Index index = getIndexObject();
                index.getRmSet().add(filename);
                index.saveIndex();
                /* Remove */
                File file = new File(filename);
                file.delete();
            }

            /* Conflict */
            /* Both modified, and differently */
            boolean modifiedDifferently =
                    isTracked(filename, splitCommit)
                            && isTracked(filename, headCommit)
                            && isTracked(filename, mergeCommit)
                            && isModified(filename, headCommit, splitCommit)
                            && isModified(filename, mergeCommit, splitCommit)
                            && isModified(filename, headCommit, mergeCommit);
            /* Absent in one commit and modified in the other */
            boolean deletedAndModified =
                    isTracked(filename, splitCommit)
                    && (!isTracked(filename, headCommit)
                            && isTracked(filename, mergeCommit)
                            && isModified(filename, mergeCommit, splitCommit)
                            || !isTracked(filename, mergeCommit)
                    && isTracked(filename, headCommit)
                    && isModified(filename, headCommit, splitCommit));
            /* Absent in split point and have different content */
            boolean createdDifferently =
                    !isTracked(filename, splitCommit)
                    && isTracked(filename, headCommit)
                    && isTracked(filename, mergeCommit)
                    && isModified(filename, headCommit, mergeCommit);
            isConflict = modifiedDifferently
                    || deletedAndModified
                    || createdDifferently;

            if (isConflict) {
                String headBlobID = headFileMap.get(filename);
                String mergeBlobID = mergeFileMap.get(filename);
                String headContent = headBlobID == null
                        ? "" : getBlob(headBlobID).getContent();
                String mergeContent = mergeBlobID == null
                        ? "" : getBlob(mergeBlobID).getContent();
                String newContent = "<<<<<<< HEAD" + "\n" + headContent
                        + "=======" + "\n" + mergeContent + ">>>>>>>" + "\n";
                Utils.writeContents(new File(filename), newContent);
                stageFileAdd(filename);
            }
        }

        /* Commit */
        String log =
                "Merged " + branchName + " into " + getHeadBranchName() + ".";
        commit("commit", log, mergeCommitID);
        if (isConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /**
     * Saves the given login information under the given remote name.
     * @param args Arrays in format:
     *             {add-remote [remote name] [name of remote directory]/.gitlet}
     */
    private static void addRemote(String... args) {
        if (args.length != 3 || !args[2].endsWith("/.gitlet")) {
            exitWithError("Incorrect operands.");
        }

        String remoteName = args[1];
        String remoteGitPath = args[2];

        File remote = new File(REMOTES + "/" + remoteName);
        if (remote.exists()) {
            exitWithError("A remote with that name already exists.");
        }

        Remote newRemote = new Remote(remoteName, remoteGitPath);
        newRemote.saveRemote();
    }

    /**
     * Remove information associated with the given remote.
     * @param args Arrays in format: {rm-remote, [remote name]}
     */
    private static void rmRemote(String... args) {
        if (args.length != 2) {
            exitWithError("Incorrect operands.");
        }
        String remoteName = args[1];
        File remote = new File(REMOTES + "/" + remoteName);
        if (!remote.exists()) {
            exitWithError("A remote with that name does not exist.");
        }
        remote.delete();
    }

    /**
     * Append the future commits to remote branch and
     * fast-forward. Only works if the remote branch's
     * head is in the history of the current local head.
     * @param args Array in format: {push, [remote name], [remote branch name]}
     */
    private static void push(String... args) {
        if (args.length != 3) {
            exitWithError("Incorrect operands.");
        }
        String remoteName = args[1];
        String branchName = args[2];
        Remote remote = getRemote(remoteName);
        String remoteGitPath = remote.getRemoteGitPath();
        File remoteGit = new File(remoteGitPath);
        if (!remoteGit.exists()) {
            exitWithError("Remote directory not found.");
        }

        /* Check if remote branch is in the history of current branch */
        File remoteBranch = new File(remoteGitPath
                + separator + "refs" + separator + branchName);
        String remoteCommitID = Utils.readContentsAsString(remoteBranch);
        Commit headCommit = getHead();
        Set<String> headAncestors = new HashSet<>();
        addAncestors(headAncestors, headCommit);
        if (!headAncestors.contains(remoteCommitID)) {
            exitWithError("Please pull down remote changes before pushing.");
        }

        /* Append future commits to remote */
        /* Copy commits and blobs*/
        Commit commitToCopy = getHead();
        Commit headCommitToAdd = commitToCopy;
        commitToCopy.setRemoteBranchAsMe(branchName, remoteGitPath);
        while (!Utils.sha1(Utils.serialize(commitToCopy)).
                equals(remoteCommitID)) {
            commitToCopy.saveCommitRemote(remoteGitPath);
            Map<String, String> fileMap = commitToCopy.getFileMap();
            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                String blobID = entry.getValue();
                Blob blobToCopy = getBlob(blobID);
                blobToCopy.saveBlobRemote(remoteGitPath);
            }
            commitToCopy = getCommit(commitToCopy.getParentID());
        }
        /* Fast-forwarding */
        String remotePath = getRemotePath(remoteGitPath);
        checkoutRemoteCommit(headCommitToAdd, remotePath);
    }

    /**
     * copies all commits and blobs from the given branch in
     * the remote repository into a local branch.
     * [remote name]/[remote branch name]
     * @param args Array in format: {fetch, [remote name], [remote branch name]}
     */
    private static void fetch(String... args) {
        if (args.length != 3) {
            exitWithError("Incorrect operands.");
        }
        String remoteName = args[1];
        String remoteBranchName = args[2];
        String branchName = remoteName + "/" + remoteBranchName;
        Remote remote = getRemote(remoteName);
        if (!remote.remoteGitExist()) {
            exitWithError("Remote directory not found.");
        }
        String remoteGitPath = remote.getRemoteGitPath();
        Commit remoteBranchCommit = getRemoteBranch(remoteGitPath,
                remoteBranchName);
        remoteBranchCommit.setBranchAsMe(branchName);

        while (remoteBranchCommit != null) {
            remoteBranchCommit.saveCommit();
            Map<String, String> fileMap = remoteBranchCommit.getFileMap();
            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                String blobID = entry.getValue();
                Blob blobToCopy = getRemoteBlob(remoteGitPath, blobID);
                blobToCopy.saveBlob();
            }
            remoteBranchCommit = getRemoteCommit(remoteGitPath,
                    remoteBranchCommit.getParentID());
        }
    }

    /**
     * Fetches branch [remote name]/[remote branch name] as for the
     * fetch command, and then merges that fetch into the current branch.
     * @param args Array in format: {pull, [remote name], [remote branch name]}
     */
    private static void pull(String... args) {
        if (args.length != 3) {
            exitWithError("Incorrect operands.");
        }
        String remoteName = args[1];
        String remoteBranchName = args[2];
        fetch("fetch", remoteName, remoteBranchName);
        merge("merge", remoteName + "/" + remoteBranchName);
    }

    /**
     * Get the head commit of a remote branch.
     * @param remoteGitPath path of a remote repository
     * @param branchName branch
     * @return the head commit
     */
    private static Commit getRemoteBranch
    (String remoteGitPath, String branchName) {
        File commitFile = new File(remoteGitPath + "/refs/" + branchName);
        if (!commitFile.exists()) {
            exitWithError("That remote does not have that branch.");
        }
        String remoteBranchCommitID = Utils.readContentsAsString(commitFile);
        return getRemoteCommit(remoteGitPath, remoteBranchCommitID);
    }

    /**
     * Return a blob from a remote repository.
     * @param remoteGitPath path of a remote repository
     * @param blobID blobID
     * @return blob
     */
    private static Blob getRemoteBlob(String remoteGitPath, String blobID) {
        if (blobID == null) {
            return null;
        }
        File blobFile = new File(remoteGitPath + "/blobs/" + blobID);
        return Utils.readObject(blobFile, Blob.class);
    }

    /**
     * Return commit from remote.
     * @param remoteGitPath path of the remote repository
     * @param commitID uid of the commit
     */
    private static Commit getRemoteCommit
    (String remoteGitPath, String commitID) {
        if (commitID == null) {
            return null;
        }
        if (commitID.length() == UID_LENGTH) {
            File commitFile = new File(remoteGitPath + "/commits/"
                    + commitID);
            if (!commitFile.exists()) {
                exitWithError("No commit with that id exists.");
            }
            return Utils.readObject(commitFile, Commit.class);
        } else if (commitID.length() < UID_LENGTH) {
            List<String> commitIDs = Utils.plainFilenamesIn(remoteGitPath
                    + "/commits");
            for (String id : commitIDs) {
                if (id.startsWith(commitID)) {
                    File commitFile = new File(remoteGitPath
                            + "/commits/" + commitID);
                    return Utils.readObject(commitFile, Commit.class);
                }
            }
            exitWithError("No commit with that id exists.");
        } else {
            exitWithError("No commit with that id exists.");
        }
        return null;
    }

    /** Return remote REMOTENAME. */
    private static Remote getRemote(String remoteName) {
        File remote = new File(REMOTES + "/" + remoteName);
        if (remote.exists()) {
            return Utils.readObject(remote, Remote.class);
        } else {
            return null;
        }
    }

    /**
     * Add file to staging area for addition.
     * @param filename file
     */
    private static void stageFileAdd(String filename) {
        Index index = getIndexObject();
        /* Create Blob for this file */
        String fileContent = Utils.readContentsAsString(new File(filename));
        Blob newBlob = new Blob(fileContent);
        newBlob.saveBlob();
        /* Add file to staging area */
        String blobID = Utils.sha1(fileContent);
        index.getAddMap().put(filename, blobID);
        index.saveIndex();
    }

    /**
     * Return the split point commit of the tow given commit.
     * for multiple split points, return the one closet to commit2
     * @param commit1 1st commit
     * @param commit2 2nd commit
     * @return the split point commit
     */
    private static Commit getSplitPoint(Commit commit1, Commit commit2) {
        /* Add ancestors of commit1 to a set */
        Set<String> commit1Ancestors = new HashSet<>();
        addAncestors(commit1Ancestors, commit1);
        return findCommitInAncestors(commit1Ancestors, commit2);
    }

    /**
     * Traverse from commit backwards, find a commit that is in a given set.
     * Use BFS to return the closet commit.
     * @param commitAncestors a given commit set
     * @param commit given commit
     * @return the closet commit that is in the set
     */
    private static Commit findCommitInAncestors
    (Set<String> commitAncestors, Commit commit) {
        Queue<Commit> queue = new ArrayDeque<>();
        queue.add(commit);

        while (!queue.isEmpty()) {
            Commit commitToReview = queue.poll();
            if (commitAncestors.contains
                    (Utils.sha1(Utils.serialize(commitToReview)))) {
                return commitToReview;
            }
            Commit parent1 = getCommit(commitToReview.getParentID());
            queue.add(parent1);
            if (commitToReview.getParent2ID() != null) {
                queue.add(getCommit(commitToReview.getParent2ID()));
            }
        }
        return null;
    }

    /**
     * Add ancestors of commit (including itself) into a set.
     * @param commitAncestors the set collecting ancestors of commit
     * @param commit the given commit
     */
    private static void addAncestors
    (Set<String> commitAncestors, Commit commit) {
        if (commit == null) {
            return;
        }
        commitAncestors.add(Utils.sha1(Utils.serialize(commit)));
        Commit parent1 = getCommit(commit.getParentID());
        Commit parent2 = getCommit(commit.getParent2ID());
        addAncestors(commitAncestors, parent1);
        if (parent2 != null) {
            addAncestors(commitAncestors, parent2);
        }
    }

    /**
     * Generate remote path from remote git path.
     * @param remoteGitPath path of a remote repository
     * @return remote path
     */
    private static String getRemotePath(String remoteGitPath) {
        return remoteGitPath.substring(0, remoteGitPath.length() - 8);
    }


    /**
     * Return true if file is tracked in commit.
     * @param filename filename
     * @param commit commit
     * @return true if file is tracked in commit
     */
    private static boolean isTracked(String filename, Commit commit) {
        Map<String, String> fileMap = commit.getFileMap();
        return fileMap.containsKey(filename);
    }

    /**
     * Return true if file in commit1 is modified
     * from the version in commit2. It requires file is tracked
     * in both commits.
     * @param filename filename
     * @param commit1 the 1st commit
     * @param commit2 the 2nd commit
     * @return true if file is modified from the version in commit2,
     * or doesn't exist.
     */
    private static boolean isModified
    (String filename, Commit commit1, Commit commit2) {
        Map<String, String> fileMap1 = commit1.getFileMap();
        Map<String, String> fileMap2 = commit2.getFileMap();
        String commit1FileBlobID = fileMap1.get(filename);
        String commit2FileBlobID = fileMap2.get(filename);
        return !commit1FileBlobID.equals(commit2FileBlobID);
    }

    /**
     * Take the version of the file in the commit, put it in
     * working directory, overwritting the version of file
     * that's already there if there is a one.
     * If trying to overwrite untracked file, exit.
     * @param filename filename to be checkout
     * @param checkoutCommit UID of commit to find the file version
     */
    private static void checkoutFile(String filename, Commit checkoutCommit) {
        /* If trying to overwrite existing but untracked file, exit */
        File file = new File(filename);
        if (!isTracked(filename, getHead()) && file.exists()) {
            exitWithError("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
        }
        Map<String, String> checkoutFileMap = checkoutCommit.getFileMap();
        if (!checkoutFileMap.containsKey(filename)) {
            exitWithError("File does not exist in that commit.");
        }
        String blobID = checkoutFileMap.get(filename);
        Blob blob = getBlob(blobID);
        Utils.writeContents(file, blob.getContent());
    }

    /**
     * Checks out all the file tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Clear the staging area.
     * @param checkoutCommit the checkout commit
     */
    private static void checkoutCommit(Commit checkoutCommit) {
        Commit headCommit = getHead();
        Map<String, String> checkoutFileMap = checkoutCommit.getFileMap();
        Map<String, String> headFileMap = headCommit.getFileMap();
        List<String> filesInCWD =
                Utils.plainFilenamesIn(System.getProperty("user.dir"));
        /* Delete files that are tracked in current commit
        but not in checkout commit */
        for (String filename : filesInCWD) {
            if (!checkoutFileMap.containsKey(filename)
                    && headFileMap.containsKey(filename)) {
                File fileToDelete = new File(filename);
                fileToDelete.delete();
            }
        }
        /* Checkout files in checkout commit */
        for (Map.Entry<String, String> entry : checkoutFileMap.entrySet()) {
            String filename = entry.getKey();
            checkoutFile(filename, checkoutCommit);
        }
        clearStagingArea();
    }

    /**
     * Take the version of the file in the commit, put it in
     * remote repository, overwritting the version of file
     * that's already there if there is a one. If trying to
     * overwrite untracked file, exit.
     * @param filename filename to be checkout
     * @param checkoutCommit UID of commit to find the file version
     * @param remotePath path of remote repository
     */
    private static void checkoutRemoteFile
    (String filename, Commit checkoutCommit, String remotePath) {
        String remoteGitPath = remotePath + "/.gitlet";
        /* If trying to overwrite existing but untracked file, exit */
        File file = new File(remotePath + "/" + filename);
        if (!isTracked(filename, getRemoteHead(remoteGitPath))
                && file.exists()) {
            exitWithError("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
        }
        Map<String, String> checkoutFileMap = checkoutCommit.getFileMap();
        if (!checkoutFileMap.containsKey(filename)) {
            exitWithError("File does not exist in that commit.");
        }
        String blobID = checkoutFileMap.get(filename);
        Blob blob = getRemoteBlob(remoteGitPath, blobID);
        Utils.writeContents(file, blob.getContent());
    }

    /**
     * Checks out all the file in a remote repository tracked
     * by the given commit. Removes tracked files that are not
     * present in that commit. Clear the staging area.
     * @param checkoutCommit the checkout commit
     * @param remotePath path of remote repository
     */
    private static void checkoutRemoteCommit
    (Commit checkoutCommit, String remotePath) {
        String remoteGitPath = remotePath + "/.gitlet";
        Commit headCommit = getRemoteHead(remoteGitPath);
        Map<String, String> checkoutFileMap = checkoutCommit.getFileMap();
        Map<String, String> headFileMap = headCommit.getFileMap();
        List<String> filesInRD =
                Utils.plainFilenamesIn(remotePath);
        /* Delete files that are tracked in current commit
        but not in checkout commit */
        for (String filename : filesInRD) {
            if (!checkoutFileMap.containsKey(filename)
                    && headFileMap.containsKey(filename)) {
                File fileToDelete = new File(filename);
                fileToDelete.delete();
            }
        }
        /* Checkout files in checkout commit */
        for (String filename : checkoutFileMap.keySet()) {
            checkoutRemoteFile(filename, checkoutCommit, remotePath);
        }
        clearRemoteStagingArea(remoteGitPath);
    }

    /**
     * Print log message for commit, using a date formatter
     * to format the date.
     * @param commit commit to print
     * @param formater date formatter
     */
    private static void printLog(Commit commit, SimpleDateFormat formater) {
        String uid = Utils.sha1(Utils.serialize(commit));
        String timeStamp = formater.format(commit.getTimestamp().getTime());
        String logMessage = commit.getLogMessage();
        System.out.println("===");
        System.out.println("commit " + uid);
        System.out.println("Date: " + timeStamp);
        System.out.println(logMessage);
        System.out.println();
    }

    /**
     * Return true if file is staged for addition.
     * @param filename filename
     */
    private static boolean isStagedForAdd(String filename) {
        Index indexObject = getIndexObject();
        Map<String, String> addMap = indexObject.getAddMap();
        return addMap.containsKey(filename);
    }

    /**
     * Return true if file is staged for removal.
     * @param filename filename
     */
    private static boolean isStagedForRM(String filename) {
        Index indexObject = getIndexObject();
        Set<String> rmSet = indexObject.getRmSet();
        return rmSet.contains(filename);
    }

    /** Return index object (staging area) of current repository. */
    private static Index getIndexObject() {
        File index = new File(INDEX);
        return Utils.readObject(index, Index.class);
    }

    /** Return uid of head commit. */
    private static String getHeadCommitID() {
        File head = new File(HEAD);
        File headBranch = new File(Utils.readContentsAsString(head));
        return Utils.readContentsAsString(headBranch);
    }

    /** Return head commit. */
    private static Commit getHead() {
        return getCommit(getHeadCommitID());
    }

    /**
     * Get commit uid of the head commit of a remote
     * repository.
     * @param remoteGitPath remote repository path
     * @return head commit
     */
    private static String getRemoteHeadCommitID(String remoteGitPath) {
        File head = new File(remoteGitPath + "/HEAD");
        File headBranch = new File(Utils.readContentsAsString(head));
        return Utils.readContentsAsString(headBranch);
    }

    /**
     * Get head commit of a remote repository.
     * @param remoteGitPath remote repository path
     * @return head commit
     */
    private static Commit getRemoteHead(String remoteGitPath) {
        return getRemoteCommit(remoteGitPath,
                getRemoteHeadCommitID(remoteGitPath));
    }

    /**
     * Return commit pointed by branch.
     * @param branchName branch name
     * @param isMerge call from merge
     * @return commit pointed by branch
     */
    private static Commit getBranch(String branchName, boolean isMerge) {
        return getCommit(getBranchCommitID(branchName, isMerge));
    }

    /**
     * Return commitID pointed by branch.
     * @param branchName branch name
     * @param isMerge call from merge
     * @return commitID pointed by branch
     */
    private static String getBranchCommitID
    (String branchName, boolean isMerge) {
        File commitFile = new File(REFS + "/" + branchName);
        if (!commitFile.exists()) {
            if (isMerge) {
                exitWithError("A branch with that name does not exist.");
            }
            exitWithError("No such branch exists.");
        }
        return Utils.readContentsAsString(commitFile);
    }

    /** Return the branch name of head branch. */
    private static String getHeadBranchName() {
        File head = new File(HEAD);
        String headCommitPath = Utils.readContentsAsString(head);
        String[] headPathList = headCommitPath.split("/");
        String headBranchName;
        if (headPathList.length == 4) {
            headBranchName = headPathList[2] + "/" + headPathList[3];
        } else {
            headBranchName = headPathList[headPathList.length - 1];
        }
        return headBranchName;
    }

    /**
     * Get a commit using uid.
     * Support abbreviation of uid.
     * @param commitID uid of the commit
     * @return commit
     */
    public static Commit getCommit(String commitID) {
        if (commitID == null) {
            return null;
        }
        if (commitID.length() == UID_LENGTH) {
            File commitFile = new File(COMMITS + "/" + commitID);
            if (!commitFile.exists()) {
                exitWithError("No commit with that id exists.");
            }
            return Utils.readObject(commitFile, Commit.class);
        } else if (commitID.length() < UID_LENGTH) {
            List<String> commitIDs = Utils.plainFilenamesIn(COMMITS);
            for (String id : commitIDs) {
                if (id.startsWith(commitID)) {
                    File commitFile = new File(COMMITS + "/" + id);
                    return Utils.readObject(commitFile, Commit.class);
                }
            }
            exitWithError("No commit with that id exists.");
        } else {
            exitWithError("No commit with that id exists.");
        }
        return null;
    }

    /** Clear the staging area of current repository. */
    private static void clearStagingArea() {
        Index index = new Index();
        index.saveIndex();
    }

    /**
     * Clear the staging area of a remote repository.
     * @param remoteGitPath remote repository path
     */
    private static void clearRemoteStagingArea(String remoteGitPath) {
        Index index = new Index();
        index.saveRemoteIndex(remoteGitPath);
    }

    /**
     * Get a blob from current repository using uid.
     * @param blobID uid of the blob
     * @return blob found
     */
    private static Blob getBlob(String blobID) {
        if (blobID == null) {
            return null;
        }
        File blobFile = new File(BLOBS + "/" + blobID);
        return Utils.readObject(blobFile, Blob.class);
    }

    /**
     * Prints out an error message and exits with error code 0.
     * @param message message to print
     */
    private static void exitWithError(String message) {
        if (message != null && !message.equals("")) {
            System.out.println(message);
        }
        System.exit(0);
    }

    /** Path of `commits` folder. */
    static final String COMMITS = ".gitlet/commits";
    /** Path of `blobs` folder. */
    static final String BLOBS = ".gitlet/blobs";
    /** Path of `refs` folder. */
    static final String REFS = ".gitlet/refs";
    /** Path of `HEAD`. */
    static final String HEAD = ".gitlet/HEAD";
    /** Path of `index`. */
    static final String INDEX = ".gitlet/index";
    /** Path of `Remote`. */
    static final String REMOTES = ".gitlet/remotes";
    /** Length of uid used in Gitlet. */
    static final int UID_LENGTH = 40;
}
