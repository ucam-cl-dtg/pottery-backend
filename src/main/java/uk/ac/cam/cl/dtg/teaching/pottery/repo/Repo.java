/*
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015-2018 BlueOptima Limited, Andrew Rice (acr31@cam.ac.uk)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.cam.cl.dtg.teaching.docker.ApiUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.FourLevelLock;
import uk.ac.cam.cl.dtg.teaching.pottery.FourLevelLock.AutoCloseableLock;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.database.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.NoHeadInRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoFileNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoTagNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.model.RepoInfoWithStatus;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.task.ParameterisationResult;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskDetail;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;

/**
 * A repo represents the candidate's attempt at a task.
 *
 * <p>We store the local version of the repo in a directory in a non-bare git repo. File updates and
 * tags etc. are made to this repo directly.
 *
 * <p>When we run the tests we make a clone of the repo directory at the specified tag so as to test
 * in isolation of new changes
 *
 * <p>If there is a remote set then we don't store the local version but we do make a clone for each
 * test. All the apis for listing the contents of the repo and changing it are disabled when we use
 * a remote.
 */
public class Repo {

  protected static final Logger LOG = LoggerFactory.getLogger(Repo.class);

  protected static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String PARAMETERISATION_WORKER_NAME = "Parameterisation worker";

  private volatile RepoInfo repoInfo;
  private volatile boolean ready = false;

  /** The directory holding this repository. */
  private final File repoDirectory;

  private final File repoTestingDirectory;

  private final String webtagPrefix;

  /**
   * If you are modifying the fields of this object then you should hold this lock. Do not hold this
   * lock whilst executing a long running task since there are api calls for polling these fields.
   */
  private final Object lockFields = new Object();

  /** Protects access to the git repo and working directory. */
  private final FourLevelLock lock = new FourLevelLock();

  /**
   * A map of submissions that have been tested. Keys are tag and an action separated by a comma.
   * You can only have one per tag/action pair.
   */
  private final ConcurrentHashMap<String, Submission> activeSubmissions = new ConcurrentHashMap<>();

  private Repo(RepoInfo repoInfo, RepoConfig c) {
    this.repoInfo = repoInfo;
    this.repoDirectory = c.getRepoDir(repoInfo.getRepoId());
    this.repoTestingDirectory = c.getRepoTestingDir(repoInfo.getRepoId());
    this.webtagPrefix = c.getWebtagPrefix();
  }

  /**
   * Create a repo object for an existing repository. Use RepoFactory rather than calling this
   * method directly.
   *
   * @param repoId the ID of the repo to open
   * @param config server configuration
   * @param database database connection
   * @return a repo object for this repository
   * @throws RepoNotFoundException if the repository does not exist or if it can't be opened
   */
  static Repo openRepo(String repoId, RepoConfig config, Database database)
      throws RepoNotFoundException {

    File repoDirectory = config.getRepoDir(repoId);

    try (TransactionQueryRunner q = database.getQueryRunner()) {
      RepoInfo r = RepoInfos.getByRepoId(repoId, q);
      if (r != null) {
        if (r.getRemote().equals(RepoInfo.REMOTE_UNSET) && !repoDirectory.exists()) {
          throw new RepoNotFoundException("Failed to find repository directory " + repoDirectory);
        }
        Repo repo = new Repo(r, config);
        if (repo.repoInfo.getExpiryDate() != null) {
          // Synchronize shouldn't be needed since we haven't given this to anyone yet, but being
          // consistent
          synchronized (repo.lockFields) {
            // If the expiry date is set, then this repo was marked as ready previously, so set
            // ready to true here
            repo.ready = true;
          }
        }
        return repo;
      } else {
        throw new RepoNotFoundException(
            "Repository with ID " + repoId + " does not exist in database");
      }
    } catch (SQLException e) {
      throw new RepoNotFoundException(
          "Failed to lookup repository with ID " + repoId + " in database", e);
    }
  }

  /**
   * Create a new repository and return an appropriate repo object. Use RepoFactory rather than
   * calling this method directly.
   */
  static Repo createRepo(RepoInfo repoInfo, RepoConfig config, Database database)
      throws RepoStorageException {

    if (repoInfo.isRemote()) {
      try {
        Git.lsRemoteRepository().setRemote(repoInfo.getRemote()).call();
      } catch (GitAPIException e) {
        throw new RepoStorageException("Failed to find remote repository", e);
      }
      try (TransactionQueryRunner t = database.getQueryRunner()) {
        RepoInfos.insert(repoInfo, t);
        t.commit();
      } catch (SQLException e) {
        throw new RepoStorageException("Failed to store repository details", e);
      }
    } else {
      File repoDirectory = config.getRepoDir(repoInfo.getRepoId());
      try (FileUtil.AutoDelete createdDirectory = FileUtil.mkdirWithAutoDelete(repoDirectory)) {
        try {
          Git.init().setDirectory(repoDirectory).call().close();
        } catch (GitAPIException e) {
          throw new RepoStorageException("Failed to initialise git repository", e);
        }
        try (TransactionQueryRunner t = database.getQueryRunner()) {
          RepoInfos.insert(repoInfo, t);
          t.commit();
        } catch (SQLException e) {
          throw new RepoStorageException("Failed to store repository details", e);
        }
        createdDirectory.persist();
      } catch (IOException e) {
        throw new RepoStorageException("Failed to create repo directory", e);
      }
    }
    return new Repo(repoInfo, config);
  }

  interface FileGetter {
    List<String> get() throws IOException;
  }

  public void commitFiles(String message, FileGetter getFiles)
      throws RepoExpiredException, RepoStorageException {
    throwIfRepoExpired();
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeFileWritingLock()) {
      try (Git git = Git.open(repoDirectory)) {
        try {
          List<String> copiedFiles = getFiles.get();
          if (!copiedFiles.isEmpty()) {
            for (String f : copiedFiles) {
              git.add().addFilepattern(f).call();
            }
            git.commit().setMessage(message).call();
          }
        } catch (IOException | GitAPIException e) {
          try {
            git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
            throw new RepoStorageException(
                "An error occurred when copying and adding to repository. Rolled back", e);
          } catch (GitAPIException | JGitInternalException e1) {
            e1.addSuppressed(e);
            throw new RepoStorageException("Failed to rollback failed update", e1);
          }
        }
      } catch (IOException e2) {
        throw new RepoStorageException("Failed to open repository", e2);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for file writing lock", e);
    }
  }

  /** Recursively copy all files from the given sourceLocation and add them to the repository. */
  public void copyAndCommitSkeletonFiles(TaskCopy task)
      throws RepoStorageException, RepoExpiredException {
    commitFiles("Copied files", () -> task.copySkeleton(repoDirectory, repoInfo.getVariant()));
  }

  /** Parameterise this task. */
  public void doParameterisation(
      Worker w,
      Database database,
      TaskCopy c,
      Runnable successCallback,
      Consumer<String> failureCallback)
      throws RepoStorageException, RepoExpiredException {
    LOG.info("Doing parameterisation with " + c.getDetail().getParameterisation());
    if (c.getDetail().getParameterisation() != null) {
      w.schedule(
          new Job() {
            @Override
            public int execute(
                TaskIndex taskIndex,
                RepoFactory repoFactory,
                ContainerManager containerManager,
                Database database) {
              Task t;
              try {
                t = taskIndex.getTask(repoInfo.getTaskId());
              } catch (TaskNotFoundException e) {
                LOG.error("Fault loading task for repo " + getRepoId(), e);
                failureCallback.accept(e.getMessage());
                return STATUS_FAILED;
              }
              ContainerExecResponse response;
              try (TaskCopy c =
                  repoInfo.isUsingTestingVersion()
                      ? t.acquireTestingCopy()
                      : t.acquireRegisteredCopy()) {
                try (AutoCloseableLock ignored = lock.takeFileWritingLock()) {
                  response = containerManager.runParameterisation(c, repoDirectory, repoInfo);
                  if (response.status() != ContainerExecResponse.Status.COMPLETED) {
                    failureCallback.accept(
                        "Failed to run parameterisation. Error was: " + response.response());
                    return STATUS_FAILED;
                  }
                  try {
                    ParameterisationResult parameterisationResult =
                        objectMapper.readValue(response.response(), ParameterisationResult.class);

                    if (parameterisationResult.getFiles() == null) {
                      throw new IOException("No files returned from parameterisation");
                    }
                    commitFiles("Parameterised files", parameterisationResult::getFiles);

                    saveProblemStatement(database, parameterisationResult.getProblemStatement());
                    successCallback.run();
                    return Job.STATUS_OK;
                  } catch (RepoStorageException | RepoExpiredException | IOException e) {
                    LOG.error("Fault recording task is ready", e);
                    failureCallback.accept(e.getMessage());
                    return STATUS_FAILED;
                  }
                } catch (InterruptedException e) {
                  return STATUS_RETRY;
                }
              } catch (TaskNotFoundException | ApiUnavailableException e) {
                LOG.error("doParameterisation failed due to exception", e);
                failureCallback.accept(e.getMessage());
                return STATUS_FAILED;
              }
            }

            @Override
            public String getDescription() {
              return "Parameterising repo" + repoInfo.getRepoId();
            }
          });
    } else {
      if (!repoInfo.isRemote()) {
        copyAndCommitSkeletonFiles(c);
      }
      saveProblemStatement(database, c.getDetail().getProblemStatement());
      successCallback.run();
    }
  }

  private void saveProblemStatement(Database database, String problemStatement)
      throws RepoStorageException {
    synchronized (lockFields) {
      this.repoInfo = this.repoInfo.withProblemStatement(problemStatement);
      try (TransactionQueryRunner t = database.getQueryRunner()) {
        RepoInfos.update(repoInfo, t);
        t.commit();
      } catch (SQLException e) {
        throw new RepoStorageException("Failed to store repository details", e);
      }
    }
  }

  void markReady(Database database, int validityMinutes) throws RepoStorageException {
    synchronized (lockFields) {
      this.ready = true;
      Calendar cal = Calendar.getInstance();
      if (validityMinutes == -1) {
        cal.add(Calendar.YEAR, 1000);
      } else {
        cal.add(Calendar.MINUTE, validityMinutes);
      }
      Date expiryDate = cal.getTime();
      this.repoInfo = this.repoInfo.withExpiryDate(expiryDate);
      try (TransactionQueryRunner t = database.getQueryRunner()) {
        RepoInfos.update(repoInfo, t);
        t.commit();
      } catch (SQLException e) {
        throw new RepoStorageException("Failed to store repository details", e);
      }
    }
  }

  public void markError(Database database, String message) throws RepoStorageException {
    synchronized (lockFields) {
      this.repoInfo = this.repoInfo.withError(message);
      try (TransactionQueryRunner t = database.getQueryRunner()) {
        RepoInfos.update(repoInfo, t);
        t.commit();
      } catch (SQLException e) {
        throw new RepoStorageException("Failed to store repository details", e);
      }
    }
  }

  /**
   * Return the submission object for the given tag. Each tagged version in the repository can only
   * be submitted at most once. Poll this method to get updates on the submission testing process
   * for the tag under test or submitted for testing.
   */
  public Submission getSubmission(String tag, String action, Database database)
      throws SubmissionStorageException, SubmissionNotFoundException {
    synchronized (lockFields) {
      Submission s = activeSubmissions.get(getSubmissionKey(tag, action));
      if (s != null) {
        return s;
      }
    }
    try (TransactionQueryRunner q = database.getQueryRunner()) {
      Submission s = Submissions.getByRepoIdAndTagAndAction(repoInfo.getRepoId(), tag, action, q);
      if (s == null) {
        throw new SubmissionNotFoundException(
            "Failed to find a submission with tag "
                + tag
                + " on repository "
                + repoInfo.getRepoId());
      }
      return s;
    } catch (SQLException e) {
      throw new SubmissionStorageException("Failed to load submission from database", e);
    }
  }

  /** Internal method to update the submission. */
  private void updateSubmission(Submission s) {
    activeSubmissions.put(getSubmissionKey(s.getTag(), s.getAction()), s);
  }

  /** Convenience method for updating a submission from a builder. */
  private void updateSubmission(Submission.Builder builder) {
    LOG.info("Submission status {}", builder.build());
    updateSubmission(builder != null ? builder.build() : null);
  }

  /** Schedule a particular version of the repo for testing later. */
  public Submission scheduleSubmission(String tag, String action, Worker w, Database db)
      throws RepoExpiredException, SubmissionStorageException, RepoStorageException {
    throwIfRepoExpired();

    if (tag.equals("HEAD")) {
      return scheduleSubmission(resolveHeadSha(), action, w, db);
    }

    Submission currentSubmission;
    Submission.Builder builder;
    synchronized (lockFields) {
      // lock here to allow us to notice that there is no submission and start a new
      // one atomically.
      try {
        // Means we've already scheduled (and possibly already run) the test for this tag
        return getSubmission(tag, action, db);
      } catch (SubmissionNotFoundException e) {
        // Lets make one
      }

      builder = Submission.builder(repoInfo.getRepoId(), tag, action);
      currentSubmission = builder.build();
      updateSubmission(currentSubmission);
    }

    w.schedule(
        new Job() {
          @Override
          public int execute(
              TaskIndex taskIndex,
              RepoFactory repoFactory,
              ContainerManager containerManager,
              Database database) {
            updateSubmission(builder.setStarted());
            Task t;
            try {
              t = taskIndex.getTask(repoInfo.getTaskId());
            } catch (TaskNotFoundException e1) {
              updateSubmission(builder.addErrorMessage("Task no longer available"));
              return STATUS_FAILED;
            }
            try (TaskCopy c =
                repoInfo.isUsingTestingVersion()
                    ? t.acquireTestingCopy()
                    : t.acquireRegisteredCopy()) {
              try (AutoCloseableLock ignored = lock.takeFileWritingLock()) {
                try {
                  setVersionToTest(tag);
                } catch (RepoStorageException e) {
                  updateSubmission(
                      builder.addErrorMessage(
                          "Failed to reset repository to requested tag (" + tag + ")"));
                  return STATUS_FAILED;
                }

                File codeDir = repoTestingDirectory;
                TaskDetail taskDetail = c.getDetail();
                int result =
                    containerManager.runSteps(
                        c,
                        codeDir,
                        taskDetail,
                        action,
                        repoInfo,
                        new ContainerManager.ErrorHandlingStepRunnerCallback() {
                          @Override
                          public void apiUnavailable(String errorMessage, Throwable exception) {
                            Repo.LOG.warn(errorMessage, exception);
                            updateSubmission(
                                builder
                                    .addErrorMessage(
                                        "Compilation failed, unable to contact the container API. "
                                            + "Retrying...")
                                    .setRetry());
                          }

                          @Override
                          public void setStatus(String status) {
                            updateSubmission(builder.setStatus(status));
                          }

                          @Override
                          public void recordErrorReason(
                              ContainerExecResponse response, String stepName) {
                            switch (response.status()) {
                              case FAILED_UNKNOWN:
                                updateSubmission(
                                    builder.addErrorMessage("Output failed, no tests were run"));
                                break;
                              case FAILED_DISK:
                                updateSubmission(
                                    builder.addErrorMessage(
                                        "Output failed, disk usage limit exceeded"));
                                break;
                              case FAILED_OOM:
                                updateSubmission(
                                    builder.addErrorMessage(
                                        "Output failed, memory usage limit exceeded"));
                                break;
                              case FAILED_TIMEOUT:
                                updateSubmission(
                                    builder.addErrorMessage(
                                        "Output failed, execution time limit exceeded"));
                                break;
                              case FAILED_OUTPUT:
                                updateSubmission(
                                    builder.addErrorMessage(
                                        "Output failed, output length limit exceeded"));
                                break;
                              case FAILED_EXITCODE:
                                updateSubmission(
                                    builder.addErrorMessage("Output failed, bad exit code"));
                                break;
                              default:
                                break;
                            }
                          }

                          @Override
                          public void startStep(String stepName) {
                            updateSubmission(builder.startStep(stepName));
                          }

                          @Override
                          public void finishStep(
                              String stepName, String status, long msec, String output) {
                            updateSubmission(builder.completeStep(stepName, status, msec, output));
                          }
                        });
                builder.setStatus(
                    result == STATUS_OK ? Submission.STATUS_COMPLETE : Submission.STATUS_FAILED);
                return result;
              } catch (InterruptedException e) {
                updateSubmission(
                    Submission.builder(repoInfo.getRepoId(), tag, action)
                        .addErrorMessage("Job was interrupted, retrying"));
                return STATUS_RETRY;
              } catch (Exception e) {
                builder.setStatus(Submission.STATUS_FAILED);
                throw e;
              } finally {
                Submission s = builder.build();
                if (!s.isNeedsRetry()) {
                  try (TransactionQueryRunner q = database.getQueryRunner()) {
                    Submissions.insert(s, q);
                    q.commit();
                  } catch (SQLException e) {
                    // This shouldn't happen, but if it does then we'll force
                    // an error message out to the user
                    updateSubmission(
                        Submission.builder(repoInfo.getRepoId(), tag, action)
                            .addErrorMessage(
                                "Failed to store result in database: " + e.getMessage()));
                    return STATUS_FAILED;
                  }
                }
                updateSubmission(s);
              }
            } catch (TaskNotFoundException e1) {
              updateSubmission(builder.addErrorMessage("Task no longer available"));
              return STATUS_FAILED;
            }
          }

          @Override
          public String getDescription() {
            return "Testing submission " + repoInfo.getRepoId() + ":" + tag;
          }
        });
    return currentSubmission;
  }

  /** Find the SHA hash for the head of the master branch. */
  public String resolveHeadSha() throws RepoStorageException {
    try {
      return Git.lsRemoteRepository()
          .setRemote(repoInfo.isRemote() ? repoInfo.getRemote() : repoDirectory.getPath())
          .setHeads(true).call().stream()
          .filter(ref -> ref.getName().equals("refs/heads/master"))
          .map(Ref::getObjectId)
          .map(ObjectId::getName)
          .findFirst()
          .orElseThrow(
              () -> new RefNotFoundException("Failed to find reference named refs/heads/master"));
    } catch (GitAPIException e) {
      throw new RepoStorageException("Failed to resolve SHA1 for refs/heads/master", e);
    }
  }

  private void throwIfRepoExpired() throws RepoExpiredException {
    if (isExpired()) {
      throw new RepoExpiredException(
          "This repository expired at " + repoInfo.getExpiryDate() + " and is no longer editable");
    }
  }

  private void throwIfRemote() throws RepoStorageException {
    if (repoInfo.isRemote()) {
      throw new RepoStorageException(
          "This repository is remote and so cannot be accessed locally.");
    }
  }

  /**
   * Update the checkout of this repo that we use for testing to point to a new tag.
   *
   * @param tag the tag to update the test to point to
   * @throws RepoStorageException if something goes wrong
   */
  private void setVersionToTest(String tag) throws RepoStorageException {
    try (AutoCloseableLock ignored = lock.takeFileWritingLock()) {
      if (repoTestingDirectory.exists()) {
        try {
          FileUtil.deleteRecursive(repoTestingDirectory);
        } catch (IOException e) {
          throw new RepoStorageException("Failed to delete previous testing directory", e);
        }
      }
      if (!repoTestingDirectory.mkdirs()) {
        throw new RepoStorageException("Failed to create directory for holding test checkout");
      }

      try (Git g =
          Git.cloneRepository()
              .setURI(repoInfo.isRemote() ? repoInfo.getRemote() : repoDirectory.getPath())
              .setDirectory(repoTestingDirectory)
              .call()) {
        g.checkout().setName(tag).call();
      } catch (GitAPIException e) {
        throw new RepoStorageException("Failed to clone repository", e);
      } catch (JGitInternalException e) {
        throw new RepoStorageException("Failed to clone repository", e.getCause());
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for file writing lock", e);
    }
  }

  /**
   * Check if tag is defined in this repository.
   *
   * @param tag to check for
   * @return true if tag exists
   * @throws RepoStorageException if something goes wrong
   */
  public boolean existsTag(String tag) throws RepoStorageException {
    try (AutoCloseableLock ignored = lock.takeGitDbOpLock()) {
      try (Git git = Git.open(repoDirectory)) {
        return git.getRepository().resolve(Constants.R_TAGS + tag) != null;
      } catch (IOException e) {
        throw new RepoStorageException(
            "Failed to lookup tag in repository " + repoInfo.getRepoId(), e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for git operation lock", e);
    }
  }

  /**
   * List the files in this repo tagged with a particular tag.
   *
   * @param tag the tag of interest
   * @return a list of file names relative to the root of the repository
   */
  public ImmutableList<String> listFiles(String tag)
      throws RepoStorageException, RepoTagNotFoundException {
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeFileReadingLock()) {
      try (Git git = Git.open(repoDirectory)) {
        Repository repo = git.getRepository();
        RevWalk revWalk = new RevWalk(repo);

        try {
          ImmutableList.Builder<String> builder = ImmutableList.builder();
          RevTree tree = getRevTree(tag, repo, revWalk);
          try (TreeWalk treeWalk = new TreeWalk(repo)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
              builder.add(treeWalk.getPathString());
            }
          }
          revWalk.dispose();
          return builder.build();
        } catch (NoHeadInRepoException e) {
          return ImmutableList.of();
        }
      } catch (IOException e) {
        throw new RepoStorageException(
            "Failed to access files in repository " + repoInfo.getRepoId() + " under tag " + tag,
            e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for file reading lock", e);
    }
  }

  /**
   * Create a new tag in this repository.
   *
   * @return the name of the tag
   */
  public String createNewTag() throws RepoStorageException, RepoExpiredException {
    throwIfRepoExpired();
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeGitDbOpLock()) {
      try (Git git = Git.open(repoDirectory)) {
        List<Ref> tagList;
        try {
          tagList = git.tagList().call();
        } catch (GitAPIException e) {
          throw new RepoStorageException("Failed to list all tags in repo", e);
        }

        String prefix = Constants.R_TAGS + webtagPrefix;
        int max = -1;
        for (Ref tag : tagList) {
          String tagName = tag.getName();
          if (tagName.startsWith(prefix)) {
            int value;
            try {
              value = Integer.parseInt(tagName.substring(prefix.length()));
            } catch (NumberFormatException e) {
              throw new RepoStorageException("Failed to parse tag name " + tagName, e);
            }
            if (value > max) {
              max = value;
            }
          }
        }

        String newTag = webtagPrefix + String.format("%03d", (max + 1));

        try {
          git.tag().setName(newTag).call();
        } catch (GitAPIException e) {
          throw new RepoStorageException(
              "Failed to apply tag " + newTag + " to repo " + repoInfo.getRepoId(), e);
        }
        return newTag;
      } catch (IOException e) {
        throw new RepoStorageException("Failed to open repository " + repoInfo.getRepoId(), e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for git operation lock", e);
    }
  }

  /**
   * List all tags in this repository (only tags which have the webtag prefix are returned.
   *
   * @return a list of tag names
   * @throws RepoStorageException if something goes wrong
   */
  public List<String> listTags() throws RepoStorageException {
    throwIfRemote();
    String prefix = Constants.R_TAGS + webtagPrefix;
    try (AutoCloseableLock ignored = lock.takeGitDbOpLock()) {
      try (Git g = Git.open(repoDirectory)) {
        return g.tagList().call().stream()
            .filter(t -> t.getName().startsWith(prefix))
            .map(t -> t.getName().substring(Constants.R_TAGS.length()))
            .collect(Collectors.toList());
      } catch (IOException | GitAPIException e) {
        throw new RepoStorageException("Failed to get tag list", e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for git operation lock", e);
    }
  }

  /**
   * Set the contents of the repository to be the same as at the particular tag.
   *
   * <p>Since jgit doesn't expose a multi-commit or no-commit revert command, we instead reset to
   * the earlier point, put the HEAD pointer back where it was, and then do a manual commit.
   *
   * @param tag the tag to reset to
   */
  public void reset(String tag)
      throws RepoStorageException, RepoExpiredException, RepoTagNotFoundException {
    throwIfRepoExpired();
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeFullExclusionLock()) {
      try (Git git = Git.open(repoDirectory)) {
        Repository r = git.getRepository();
        Ref tagRef = r.findRef(tag);
        if (tagRef == null) {
          throw new RepoTagNotFoundException(
              "Tag " + tag + " not found in repository " + repoInfo.getRepoId());
        }

        ResetCommand reset = git.reset().setMode(ResetType.HARD).setRef(tagRef.getName());

        reset.call();

        reset =
            git.reset()
                .setMode(ResetType.SOFT)
                .setRef(Constants.ORIG_HEAD); // The revision before the reset above

        reset.call();

        CommitCommand commit = git.commit().setMessage("Reverted to " + tag);

        commit.call();

      } catch (GitAPIException | IOException e) {
        throw new RepoStorageException("Failed to reset repo to tag " + tag, e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for full exclusion lock", e);
    }
  }

  /**
   * Delete the file specified.
   *
   * @param fileName relative to the root of the repository
   */
  public void deleteFile(String fileName)
      throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException {
    throwIfRepoExpired();
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeFileWritingLock()) {
      File f = new File(repoDirectory, fileName);
      try {
        if (!FileUtil.isParent(repoDirectory, f)) {
          throw new RepoFileNotFoundException("Invalid fileName " + fileName);
        }
      } catch (IOException e) {
        throw new RepoStorageException("Failed to perform security check on requested filename", e);
      }

      if (!f.exists()) {
        throw new RepoFileNotFoundException("File does not exist");
      }

      if (f.isDirectory()) {
        throw new RepoFileNotFoundException("File is a directory");
      }

      try (Git git = Git.open(repoDirectory)) {
        try {
          git.rm().addFilepattern(fileName).call();
          git.commit().setMessage("Removing file: " + fileName).call();
        } catch (GitAPIException e) {
          try {
            git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
            throw new RepoStorageException(
                "Failed to commit delete of " + fileName + ". Rolled back", e);
          } catch (GitAPIException e1) {
            e1.addSuppressed(e);
            throw new RepoStorageException("Failed to rollback delete of " + fileName, e1);
          }
        }
      } catch (IOException e) {
        throw new RepoStorageException("Failed to open repository " + repoInfo.getRepoId(), e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for file writing lock", e);
    }
  }

  /**
   * Replace the contents of a file.
   *
   * @param fileName the filename with path relative to the root of the repository
   * @param data to replace the contents of the file with
   */
  public void updateFile(String fileName, byte[] data)
      throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException {
    throwIfRepoExpired();
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeFileWritingLock()) {
      File f = new File(repoDirectory, fileName);
      try {
        if (!FileUtil.isParent(repoDirectory, f)) {
          throw new RepoFileNotFoundException("Invalid fileName " + fileName);
        }
      } catch (IOException e) {
        throw new RepoStorageException("Failed to perform security check on requested filename", e);
      }

      if (f.isDirectory()) {
        throw new RepoFileNotFoundException("File already exists and is a directory");
      }

      File parentFile = f.getParentFile();

      if (!parentFile.exists()) {
        if (!parentFile.mkdirs()) {
          throw new RepoStorageException("Failed to create directories for " + fileName);
        }
      }
      try (FileOutputStream fos = new FileOutputStream(f)) {
        IOUtils.write(data, fos);
      } catch (IOException e) {
        throw new RepoStorageException("Failed to write data to file " + fileName, e);
      }
      try (Git git = Git.open(repoDirectory)) {
        try {
          git.add().addFilepattern(fileName).call();
          git.commit().setMessage("Updating file " + fileName).call();
        } catch (GitAPIException e) {
          try {
            git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
            throw new RepoStorageException(
                "Failed to commit update to " + fileName + ". Rolled back", e);
          } catch (GitAPIException e1) {
            e1.addSuppressed(e);
            throw new RepoStorageException("Failed to rollback failed update to " + fileName, e1);
          }
        }
      } catch (IOException e) {
        throw new RepoStorageException("Failed to open repository", e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for file writing lock", e);
    }
  }

  /**
   * Read the contents of a particular file at a particular version.
   *
   * @param tag the tag of the version to use or HEAD
   * @param fileName the filename relative to the root of the repository
   * @return a StreamingOutput instance containing the data
   */
  public byte[] readFile(String tag, String fileName)
      throws RepoStorageException, RepoFileNotFoundException, RepoTagNotFoundException {
    throwIfRemote();
    try (AutoCloseableLock ignored = lock.takeFileReadingLock()) {
      try (Git git = Git.open(repoDirectory)) {
        Repository repo = git.getRepository();
        RevWalk revWalk = new RevWalk(repo);
        RevTree tree;
        try {
          tree = getRevTree(tag, repo, revWalk);
        } catch (NoHeadInRepoException e) {
          throw new IOException("File not found");
        }

        try (TreeWalk treeWalk = new TreeWalk(repo)) {
          treeWalk.addTree(tree);
          treeWalk.setRecursive(true);
          treeWalk.setFilter(PathFilter.create(fileName));
          if (!treeWalk.next()) {
            throw new IOException("File (" + fileName + ") not found");
          }

          ObjectId objectId = treeWalk.getObjectId(0);
          ObjectLoader loader = repo.open(objectId);
          return loader.getBytes();
        }
      } catch (IOException e) {
        throw new RepoFileNotFoundException("Failed to read file from repository", e);
      }
    } catch (InterruptedException e) {
      throw new RepoStorageException("Interrupted whilst waiting for file reading lock", e);
    }
  }

  private RevTree getRevTree(String tag, Repository repo, RevWalk revWalk)
      throws IOException, RepoStorageException, RepoTagNotFoundException {
    RevTree tree;
    try {
      ObjectId tagId =
          repo.resolve(Constants.HEAD.equals(tag) ? Constants.HEAD : Constants.R_TAGS + tag);
      if (tagId == null) {
        if (Constants.HEAD.equals(tag)) {
          throw new NoHeadInRepoException("Failed to find HEAD in repo.");
        } else {
          throw new RepoTagNotFoundException("Failed to find tag " + tag);
        }
      }
      RevCommit revCommit = revWalk.parseCommit(tagId);
      tree = revCommit.getTree();

    } catch (RevisionSyntaxException e) {
      throw new RepoStorageException("Failed to load revision for head of repository", e);
    }
    return tree;
  }

  public String getRepoId() {
    return repoInfo.getRepoId();
  }

  public String getTaskId() {
    return repoInfo.getTaskId();
  }

  public boolean isUsingTestingVersion() {
    return repoInfo.isUsingTestingVersion();
  }

  RepoInfo toRepoInfo() {
    return repoInfo;
  }

  public RepoInfoWithStatus toRepoInfoWithStatus() {
    return repoInfo.withStatus(ready);
  }

  public boolean isReady() {
    return ready;
  }

  /** Return true if this repo has expired. */
  public boolean isExpired() {
    return repoInfo.getExpiryDate() != null && new Date().after(repoInfo.getExpiryDate());
  }

  public String getSubmissionOutput(String tag, String action, String step, Database database)
      throws SubmissionNotFoundException, SubmissionStorageException {
    synchronized (lockFields) {
      Submission s = activeSubmissions.get(getSubmissionKey(tag, action));
      if (s != null) {
        return s.getSteps().stream()
            .filter(stepResult -> step.equals(stepResult.getName()))
            .findFirst()
            .orElseThrow(SubmissionNotFoundException::new)
            .getOutput();
      }
    }
    try (TransactionQueryRunner q = database.getQueryRunner()) {
      Submission s = Submissions.getByRepoIdAndTagAndAction(repoInfo.getRepoId(), tag, action, q);
      if (s == null) {
        throw new SubmissionNotFoundException(
            "Failed to find a submission with tag "
                + tag
                + " on repository "
                + repoInfo.getRepoId());
      }

      if (s.getSteps().stream().anyMatch(stepResult -> step.equals(stepResult.getName()))) {
        return Submissions.getOutputByRepoIdAndTagAndStep(
            repoInfo.getRepoId(), tag, action, step, q);
      } else {
        throw new SubmissionNotFoundException("Output named " + step + " not found");
      }

    } catch (SQLException e) {
      throw new SubmissionStorageException("Failed to load submission from database", e);
    }
  }

  private String getSubmissionKey(String tag, String action) {
    return tag + "," + action;
  }
}
