/**
 * pottery-backend - Backend API for testing programming exercises
 * Copyright © 2015 Andrew Rice (acr31@cam.ac.uk)
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
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

import uk.ac.cam.cl.dtg.teaching.docker.APIUnavailableException;
import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.FourLevelLock;
import uk.ac.cam.cl.dtg.teaching.pottery.FourLevelLock.AutoCloseableLock;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.config.RepoConfig;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerExecResponse;
import uk.ac.cam.cl.dtg.teaching.pottery.containers.ContainerManager;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Submission;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.TaskInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.NoHeadInRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoExpiredException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoFileNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoTagNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.SubmissionStorageException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.TaskNotFoundException;
import uk.ac.cam.cl.dtg.teaching.pottery.task.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskCopy;
import uk.ac.cam.cl.dtg.teaching.pottery.task.TaskIndex;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Job;
import uk.ac.cam.cl.dtg.teaching.pottery.worker.Worker;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.HarnessResponse;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Interpretation;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.ValidatorResponse;

public class Repo {
	
	protected static final Logger LOG = LoggerFactory.getLogger(Repo.class);

	private final String repoId;
	
	private final String taskId;

	private final Date expiryDate;
	
	/**
	 * The directory holding this repository.
	 */
	private final File repoDirectory;

	private final File repoTestingDirectory;

	private final String webtagPrefix;
	
	/**
	 * Set to true if this repo is for the testing version of a task rather than the registered version
	 */
	private final boolean usingTestingVersion;
	
	/**
	 * A map of submissions that have been tested. Keys are tags. You can only have one per tag.
	 */
	private ConcurrentHashMap<String, Submission> activeSubmissions;	
	
	/**
	 * If you are modifying the fields of this object then you should hold this
	 * lock. Do not hold this lock whilst executing a long running task since
	 * there are api calls for polling these fields.
	 */
	private final Object lockFields  = new Object();
	
	/**
	 * Protects access to the git repo and working directory
	 */
	private final FourLevelLock lock = new FourLevelLock();
	
	private Repo(String repoId, RepoConfig c, String taskId, boolean usingTestingVersion, Date expiryDate) {
		this.repoId = repoId;
		this.repoDirectory = c.getRepoDir(repoId);
		this.repoTestingDirectory = c.getRepoTestingDir(repoId);
		this.webtagPrefix = c.getWebtagPrefix();
		this.taskId = taskId;
		this.usingTestingVersion = usingTestingVersion;
		this.expiryDate = expiryDate;
		this.activeSubmissions = new ConcurrentHashMap<>();
	}

	/** 
	 * Recursively copy all files from the given sourceLocation and add them to the repository 
	 * @param sourceLocation the location to copy from
	 * @throws RepoStorageException
	 * @throws RepoExpiredException 
	 */
	public void copyFiles(TaskCopy task) throws RepoStorageException, RepoExpiredException {
		if (isExpired()) throw new RepoExpiredException("This repository expired at "+expiryDate+" and is no longer editable");
		
		try (AutoCloseableLock l = lock.takeFileWritingLock()) {
			try (Git git = Git.open(repoDirectory)) {
				try {
					List<String> copiedFiles = task.copySkeleton(repoDirectory);
					if (!copiedFiles.isEmpty()) {
						for(String f : copiedFiles) {
							git.add().addFilepattern(f).call();
						}
						git.commit().setMessage("Copied files").call();
					}
				} catch (IOException | GitAPIException e) {
					try {
						git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
						throw new RepoStorageException("An error occurred when copying and adding to repository. Rolled back",e);
					} catch (GitAPIException|JGitInternalException e1) {
						e1.addSuppressed(e);
						throw new RepoStorageException("Failed to rollback failed update",e1);
					}
				}
			} catch (IOException e2) {
				throw new RepoStorageException("Failed to open repository",e2);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for file writing lock",e);
		}
	}
	
	/**
	 * Return the submission object for the given tag. Each tagged version in the repository can
	 * only be submitted at most once. Poll this method to get updates on the submission testing process
	 * for the tag under test or submitted for testing.
	 * 
	 * @param tag
	 * @param database
	 * @return
	 * @throws SubmissionStorageException 
	 * @throws SubmissionNotFoundException 
	 * @throws SQLException
	 */
	public Submission getSubmission(String tag, Database database) throws SubmissionStorageException, SubmissionNotFoundException {
		synchronized (lockFields) {
			Submission s = activeSubmissions.get(tag);
			if (s != null) { 
				return s;
			}
		}	
		try (TransactionQueryRunner q = database.getQueryRunner()) {
			Submission s = Submission.getByRepoIdAndTag(repoId, tag, q);
			if (s == null) {
				throw new SubmissionNotFoundException("Failed to find a submission with tag "+tag+" on repository "+repoId);
			}
			return s;
		} catch (SQLException e) {
			throw new SubmissionStorageException("Failed to load submission from database",e);
		}
	}
	
	/**
	 * Internal method to update the submission 
	 */
	private void updateSubmission(Submission s) {
		activeSubmissions.put(s.getTag(), s);
	}
	
	/**
	 * Convenience method for updating a submission from a builder
	 * @param builder
	 */
	private void updateSubmission(Submission.Builder builder) {
		updateSubmission(builder != null ? builder.build() : null);
	}
	
	/**
	 * Schedule a particular version of the repo for testing later
	 * 
	 * @param tag
	 * @param w
	 * @param db
	 * @return
	 * @throws RepoExpiredException 
	 * @throws SubmissionStorageException 
	 * @throws RepoTagNotFoundException 
	 * @throws RepoStorageException 
	 * @throws SubmissionNotFoundException 
	 */
	public Submission scheduleSubmission(String tag, Worker w, Database db) throws RepoExpiredException, SubmissionStorageException, RepoTagNotFoundException, RepoStorageException {
		if (isExpired()) throw new RepoExpiredException("This repository expired at "+expiryDate+" and is no longer editable");
		
		synchronized (lockFields) {
			try {
				Submission s = getSubmission(tag, db);
				//	Means we've already scheduled (and possibly already run) the test for this tag
				return s;
			} catch (SubmissionNotFoundException e) {
				// Lets make one
			}
		
			if (!existsTag(tag)) {
				throw new RepoTagNotFoundException("Tag "+tag+" does not exist in repository");
			}
			
			Submission.Builder builder = Submission.builder(repoId,tag);
		
			Submission currentSubmission = builder.build();
			updateSubmission(currentSubmission);

			w.schedule(new Job() {
				@Override
				public int execute(TaskIndex taskIndex, RepoFactory repoFactory, ContainerManager containerManager, Database database) {					
					updateSubmission(builder.setStarted());
					Task t;
					try {
						t = taskIndex.getTask(taskId);
					} catch (TaskNotFoundException e1) {
						updateSubmission(builder.setCompilationResponse("Task no longer available", false,0));					
						return Job.STATUS_FAILED;
					}
					try (TaskCopy c = usingTestingVersion ? t.acquireTestingCopy() : t.acquireRegisteredCopy()) {
						try (AutoCloseableLock l = lock.takeFileWritingLock()) {						
							try {
								setVersionToTest(tag);
							} catch (RepoStorageException e) {
								updateSubmission(builder.addErrorMessage("Failed to reset repository to requested tag ("+tag+")"));
								return Job.STATUS_FAILED;
							}

							File codeDir = repoTestingDirectory;
							TaskInfo taskInfo = c.getInfo();
							String image = taskInfo.getImage();
							updateSubmission(builder.setStatus(Submission.STATUS_COMPILATION_RUNNING));
							ContainerExecResponse<String> compilationResponse;
							try {
								compilationResponse = containerManager.execCompilation(codeDir, c.getCompileRoot(), image, taskInfo.getCompilationRestrictions());
							} catch (APIUnavailableException e) {
								LOG.warn("Docker API unavailable when trying to execute compilation step. Retrying",e);
								updateSubmission(builder.addErrorMessage("Compilation failed, unable to contact the container API. Retrying...").setRetry());
								return Job.STATUS_RETRY;
							}
							updateSubmission(builder.setCompilationResponse(compilationResponse.getResponse(),compilationResponse.isSuccess(),compilationResponse.getExecutionTimeMs()));
							if (!compilationResponse.isSuccess()) {
								updateSubmission(builder.addErrorMessage("Compilation failed, no tests were run"));
								return Job.STATUS_FAILED;
							}

							updateSubmission(builder.setStatus(Submission.STATUS_HARNESS_RUNNING));
							ContainerExecResponse<HarnessResponse> harnessResponse;
							try {
								harnessResponse = containerManager.execHarness(codeDir,c.getHarnessRoot(),image,taskInfo.getHarnessRestrictions());
							} catch (APIUnavailableException e) {
								LOG.warn("Docker API unavailable when trying to run harness step. Retrying",e);
								updateSubmission(builder.addErrorMessage("Harness failed, unable to contact the container API. Retrying...").setRetry());
								return Job.STATUS_RETRY;
							}
							updateSubmission(builder.setHarnessResponse(harnessResponse.getResponse(),harnessResponse.getExecutionTimeMs()));
							updateSubmission(builder.addErrorMessage(harnessResponse.getResponse().getErrorMessage()));
							if (!harnessResponse.getResponse().isCompleted()) {
								return Job.STATUS_FAILED;
							}

							updateSubmission(builder.setStatus(Submission.STATUS_VALIDATOR_RUNNING));
							
							ContainerExecResponse<ValidatorResponse> validatorResponse;
							try {
								validatorResponse = containerManager.execValidator(c.getValidatorRoot(), harnessResponse.getResponse(), image,taskInfo.getValidatorRestrictions());
							} catch (APIUnavailableException e) {
								LOG.warn("Docker API unavailable when trying to run validator step. Retrying",e);
								updateSubmission(builder.addErrorMessage("Validation failed, unable to contact the container API. Retrying...").setRetry());
								return Job.STATUS_RETRY;
							}

							
							boolean acceptableFound = false;
							boolean badFound = validatorResponse.getResponse().getErrorMessage()!=null;
							for(Interpretation i : validatorResponse.getResponse().getInterpretations()) {
								if (i.getResult().equals(Interpretation.INTERPRETED_ACCEPTABLE)) {
									acceptableFound = true;
								}
								else if (i.getResult().equals(Interpretation.INTERPRETED_FAILED)) {
									badFound = true;
								}
							}
							
							String interpretation;
							if (badFound) {
								interpretation = Submission.INTERPRETATION_BAD;
							}
							else if (acceptableFound) {
								interpretation = Submission.INTERPRETATION_ACCEPTABLE;
							}
							else {
								interpretation = Submission.INTERPRETATION_EXCELLENT;
							}
							
							
							updateSubmission(builder.setValidatorResponse(validatorResponse.getResponse(),validatorResponse.getExecutionTimeMs())
									.setInterpretation(interpretation)
									.addErrorMessage(validatorResponse.getResponse().getErrorMessage())); 
							if (!validatorResponse.getResponse().isCompleted()) { 
								return Job.STATUS_FAILED; 
							}
						}
						catch (InterruptedException e) {
							updateSubmission(Submission.builder(repoId,tag)
									.setCompilationResponse("Job was interrupted, retrying",false,0));
							return Job.STATUS_RETRY;
						}
						finally {
							builder.setComplete();
							
							Submission s = builder.build();
							if (!s.isNeedsRetry()) {
								try (TransactionQueryRunner q = database.getQueryRunner()) {
									s.insert(q);
									q.commit();
								} catch (SQLException e) {
									// This shouldn't happen, but if it does then we'll force 
									// an error message out to the user
									updateSubmission(Submission.builder(repoId,tag)
											.addErrorMessage("Failed to store result in database: "+e.getMessage()));
									return Job.STATUS_FAILED;
								}
							}
							updateSubmission(s);
						}
					} catch (TaskNotFoundException e1) {
						updateSubmission(builder.addErrorMessage("Task no longer available"));
						return Job.STATUS_FAILED;
					}
					return Job.STATUS_OK;
				}

				@Override
				public String getDescription() {
					return "Testing submission "+repoId+":"+tag;
				}			
			});
			return currentSubmission;
		}
	}
	
	/**
	 * Update the checkout of this repo that we use for testing to point to a new tag
	 * 
	 * @param tag the tag to update the test to point to
	 * @throws RepoStorageException if something goes wrong
	 */
	private void setVersionToTest(String tag) throws RepoStorageException {
		try (AutoCloseableLock l = lock.takeFileWritingLock()) {
			if (repoTestingDirectory.exists()) {
				try {
					FileUtil.deleteRecursive(repoTestingDirectory);
				} catch (IOException e) {
					throw new RepoStorageException("Failed to delete previous testing directory",e);
				}
			}
			if (!repoTestingDirectory.mkdirs()) {
				throw new RepoStorageException("Failed to create directory for holding test checkout");
			}
		
			try (Git g = Git.cloneRepository()
				.setURI(repoDirectory.getPath())
				.setDirectory(repoTestingDirectory)
				.setBranch(tag)
				.call()) {
			} catch (GitAPIException e) {
				throw new RepoStorageException("Failed to clone repository",e);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Inerrupted whilst waiting for file writing lock",e);
		}
	}

	
	/**
	 * Check if tag is defined in this repository
	 * @param tag to check for
	 * @return true if tag exists
	 * @throws RepoStorageException if something goes wrong
	 */
	public boolean existsTag(String tag) throws RepoStorageException {
		try (AutoCloseableLock l = lock.takeGitDbOpLock()) {
			try (Git git = Git.open(repoDirectory)) {
				return git.getRepository().resolve(Constants.R_TAGS+tag) != null;
			} catch (IOException e) {
				throw new RepoStorageException("Failed to lookup tag in repository "+repoId,e);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for git operation lock",e);
		}
	}
	

	/**
	 * List the files in this repo tagged with a particular tag
	 * @param tag the tag of interest
	 * @return a list of file names relative to the root of the repository
	 * @throws RepoStorageException
	 * @throws RepoTagNotFoundException 
	 */
	public List<String> listFiles(String tag) throws RepoStorageException, RepoTagNotFoundException {
		try (AutoCloseableLock l = lock.takeFileReadingLock()) {
			try (Git git = Git.open(repoDirectory)) {
				Repository repo = git.getRepository();
				RevWalk revWalk = new RevWalk(repo);

				List<String> result;
				try {
					RevTree tree = getRevTree(tag, repo, revWalk); 
					try (TreeWalk treeWalk = new TreeWalk(repo)) {
						treeWalk.addTree(tree);
						treeWalk.setRecursive(true);
						result = new LinkedList<String>();
						while(treeWalk.next()) {
							result.add(treeWalk.getPathString());
						}
					}
					revWalk.dispose();
					return result;
				} catch (NoHeadInRepoException e) {
					return new LinkedList<String>();
				} 
			} catch (IOException e) {
				throw new RepoStorageException("Failed to access files in repository "+repoId+" under tag "+tag,e);
			}
		} catch (InterruptedException e ) {
			throw new RepoStorageException("Interrupted whilst waiting for file reading lock",e);
		}
	}

	
	/**
	 * Create a new tag in this repository
	 * @return the name of the tag
	 * @throws RepoStorageException
	 * @throws RepoExpiredException 
	 */
	public String createNewTag() throws RepoStorageException, RepoExpiredException {
		if (isExpired()) throw new RepoExpiredException("This repository expired at "+expiryDate+" and is no longer editable");
		try (AutoCloseableLock l = lock.takeGitDbOpLock()) {
			try (Git git = Git.open(repoDirectory)) {
				List<Ref> tagList;
				try {
					tagList = git.tagList().call();
				} catch (GitAPIException e) {
					throw new RepoStorageException("Failed to list all tags in repo",e);
				}
				
				String prefix = Constants.R_TAGS+webtagPrefix;
				int max = -1;
				for(Ref tag : tagList) {
					String tagName = tag.getName();
					if (tagName.startsWith(prefix)) {
						int value;
						try {
							value = Integer.parseInt(tagName.substring(prefix.length()));
						} catch (NumberFormatException e) {
							throw new RepoStorageException("Failed to parse tag name "+tagName,e);						
						}
						if (value > max) max = value;
					}				
				}
				
				String newTag = webtagPrefix + String.format("%03d", (max+1));
				
				try {
					git.tag().setName(newTag).call();
				} catch (GitAPIException e) {
					throw new RepoStorageException("Failed to apply tag "+newTag+" to repo "+repoId,e);
				}
				return newTag;
			} 
			catch (IOException e) {
				throw new RepoStorageException("Failed to open repository "+repoId,e);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for git operation lock",e);
		}
	}
	
	/**
	 * List all tags in this repository (only tags which have the webtag prefix are returned
	 * @return a list of tag names
	 * @throws RepoStorageException if something goes wrong
	 */
	public List<String> listTags() throws RepoStorageException {
		String prefix = Constants.R_TAGS+webtagPrefix;
		try (AutoCloseableLock l = lock.takeGitDbOpLock()) {
			try (Git g = Git.open(repoDirectory)) {
				return g.tagList().call().stream().
						filter(t -> t.getName().startsWith(prefix)).
						map(t -> t.getName().substring(Constants.R_TAGS.length())).
						collect(Collectors.toList());
			}
			catch (IOException|GitAPIException e) {
				throw new RepoStorageException("Failed to get tag list",e);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for git operation lock",e);
		}
	}


	/**
	 * Set the contents of the repository to be the same as at the particular tag. Note that this does a git revert and not a reset.
	 * 
	 * @param tag the tag to reset to
	 * @throws RepoStorageException if something goes wrong
	 * @throws RepoExpiredException 
	 * @throws RepoTagNotFoundException 
	 */
	public void reset(String tag) throws RepoStorageException, RepoExpiredException, RepoTagNotFoundException {
		if (isExpired()) throw new RepoExpiredException("This repository expired at "+expiryDate+" and is no longer editable");
		try (AutoCloseableLock l = lock.takeFileWritingLock()) {
			try (Git git = Git.open(repoDirectory)) {
				Repository r = git.getRepository();
				Ref tagRef = r.findRef(tag);
				if (tagRef == null) { 
					throw new RepoTagNotFoundException("Tag "+tag+" not found in repository "+repoId);
				}
				Ref peeled = r.peel(tagRef);
				ObjectId tagObjectId = peeled != null ? peeled.getPeeledObjectId() : tagRef.getObjectId();
				ObjectId headObjectId = r.findRef("HEAD").getObjectId();

				RevertCommand rev = git.revert();
				for(RevCommit c : git.log().addRange(tagObjectId,headObjectId).call()) {
					rev = rev.include(c);
				}
				rev.call();
			}
			catch (GitAPIException|IOException e) {
				throw new RepoStorageException("Failed to reset repo to tag "+tag,e);
			}
		}
		catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for file writing lock",e);
		}
	}
	

	/**
	 * Delete the file specified
	 * @param fileName relative to the root of the repository
	 * @throws RepoStorageException if we fail to delete the file
	 * @throws RepoExpiredException 
	 * @throws RepoFileNotFoundException 
	 */
	public void deleteFile(String fileName) throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException {
		if (isExpired()) throw new RepoExpiredException("This repository expired at "+expiryDate+" and is no longer editable");
		try (AutoCloseableLock l = lock.takeFileWritingLock()) {
			File f = new File(repoDirectory,fileName);
			try {
				if (!FileUtil.isParent(repoDirectory, f)) {
					throw new RepoFileNotFoundException("Invalid fileName "+fileName);
				}
			} catch (IOException e) {
				throw new RepoStorageException("Failed to perform security check on requested filename",e);
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
					git.commit().setMessage("Removing file: "+fileName).call();
				}
				catch (GitAPIException e) {
					try {
						git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
						throw new RepoStorageException("Failed to commit delete of "+fileName+". Rolled back",e);
					} catch (GitAPIException e1) {
						e1.addSuppressed(e);
						throw new RepoStorageException("Failed to rollback delete of "+fileName,e1);
					}
				}
			}
			catch (IOException e) {
				throw new RepoStorageException("Failed to open repository "+repoId,e);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for file writing lock",e);
		}
	}

	
	/**
	 * Replace the contents of a file
	 * @param fileName the filename with path relative to the root of the repository
	 * @param data to replace the contents of the file with
	 * @throws RepoStorageException
	 * @throws RepoExpiredException 
	 * @throws RepoFileNotFoundException 
	 */
	public void updateFile(String fileName, byte[] data) throws RepoStorageException, RepoExpiredException, RepoFileNotFoundException {
		if (isExpired()) throw new RepoExpiredException("This repository expired at "+expiryDate+" and is no longer editable");
		try (AutoCloseableLock l = lock.takeFileWritingLock()) {
			File f = new File(repoDirectory,fileName);
			try {
				if (!FileUtil.isParent(repoDirectory, f)) {
					throw new RepoFileNotFoundException("Invalid fileName "+fileName);
				}
			} catch (IOException e) {
				throw new RepoStorageException("Failed to perform security check on requested filename",e);
			}

			if (f.isDirectory()) {
				throw new RepoFileNotFoundException("File already exists and is a directory");
			}

			File parentFile = f.getParentFile();

			if (!parentFile.exists()) {
				if (!parentFile.mkdirs()) {
					throw new RepoStorageException("Failed to create directories for "+fileName);
				}
			}
			try(FileOutputStream fos = new FileOutputStream(f)) {
				IOUtils.write(data, fos);
			} 
			catch (IOException e) {
				throw new RepoStorageException("Failed to write data to file "+fileName,e);
			}
			try (Git git = Git.open(repoDirectory)) {
				try {
					git.add().addFilepattern(fileName).call();
					git.commit().setMessage("Updating file "+fileName).call();
				} catch (GitAPIException e) {
					try {
						git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
						throw new RepoStorageException("Failed to commit update to "+fileName+". Rolled back",e);
					} catch (GitAPIException e1) {
						e1.addSuppressed(e);
						throw new RepoStorageException("Failed to rollback failed update to "+fileName,e1);
					}
				}
			}
			catch (IOException e) {
				throw new RepoStorageException("Failed to open repository",e);
			}
		} catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for file writing lock",e);
		}
	}

	
	/**
	 * Read the contents of a particular file at a particular version
	 * @param tag the tag of the version to use or HEAD
	 * @param fileName the filename relative to the root of the repository
	 * @return a StreamingOutput instance containing the data
	 * @throws RepoStorageException if something goes wrong
	 * @throws RepoFileNotFoundException 
	 * @throws RepoTagNotFoundException 
	 */
	public StreamingOutput readFile(String tag, String fileName) throws RepoStorageException, RepoFileNotFoundException, RepoTagNotFoundException {
		try (AutoCloseableLock l = lock.takeFileReadingLock()) {
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
					if(!treeWalk.next()) {
						throw new IOException("File ("+fileName+") not found");
					}

					ObjectId objectId = treeWalk.getObjectId(0);
					ObjectLoader loader = repo.open(objectId);
					byte[] result = loader.getBytes();

					return new StreamingOutput() {
						@Override
						public void write(OutputStream output) throws IOException,
						WebApplicationException {
							output.write(result);
						}
					};
				}
			}
			catch (IOException e) {
				throw new RepoFileNotFoundException("Failed to read file from repository",e);
			}
		}
		catch (InterruptedException e) {
			throw new RepoStorageException("Interrupted whilst waiting for file reading lock",e);
		}
  	}
	

	
	
	private RevTree getRevTree(String tag, Repository repo, RevWalk revWalk)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			IOException, RepoStorageException, MissingObjectException, NoHeadInRepoException, RepoTagNotFoundException {
		RevTree tree;
		try {
			ObjectId tagId = repo.resolve(Constants.HEAD.equals(tag) ? Constants.HEAD : Constants.R_TAGS+tag);
			if (tagId == null) {
				if (Constants.HEAD.equals(tag)) {
					throw new NoHeadInRepoException("Failed to find HEAD in repo.");
				}
				else {
					throw new RepoTagNotFoundException("Failed to find tag "+tag);
				}
			}
			RevCommit revCommit = revWalk.parseCommit(tagId);
			tree = revCommit.getTree();

		} catch (RevisionSyntaxException e) {
			throw new RepoStorageException("Failed to load revision for head of repository",e);
		}
		return tree;
	}

	
	/**
	 * Create a repo object for an existing repository. Use RepoFactory rather than calling this method directly
	 *
	 * @param repoId the ID of the repo to open
	 * @param config server configuration
	 * @param database database connection
	 * @return a repo object for this repository 
	 * @throws RepoNotFoundException if the repository does not exist or if it can't be opened 
	 */
	static Repo openRepo(String repoId, RepoConfig config, Database database) throws RepoNotFoundException {
		
		File repoDirectory = config.getRepoDir(repoId);
		
		if (!repoDirectory.exists()) {
			throw new RepoNotFoundException("Failed to find repository directory "+repoDirectory);
		}
		
		try(TransactionQueryRunner q = database.getQueryRunner()) {
			RepoInfo r = RepoInfo.getByRepoId(repoId, q);
			if (r != null) {
				return new Repo(repoId,config, r.getTaskId(),r.isUsingTestingVersion(),r.getExpiryDate());
			}
			else {
				throw new RepoNotFoundException("Repository with ID "+repoId+" does not exist in database");
			}			
		}
		catch (SQLException e) {
			throw new RepoNotFoundException("Failed to lookup repository with ID "+repoId+" in database",e);
		}
		
	}
	
	/**
	 * Create a new repository and return an appropriate repo object. Use RepoFactory rather than calling this method directly
	 * 
	 * @param repoId the ID of the repo to open
	 * @param taskId is the ID of the task to begin
	 * @param usingTestingVersion indicates if we should use the registered version of the task or the testing version
	 * @param config server configuration
	 * @param database database connection
	 * @return a repo object for this repository
	 * @throws RepoStorageException if the repository couldn't be created
	 */
	static Repo createRepo(String repoId, String taskId, boolean usingTestingVersion, Date expiryDate, RepoConfig config, Database database) throws RepoStorageException {
		
		File repoDirectory = config.getRepoDir(repoId);
		
		if (!repoDirectory.mkdir()) {
			throw new RepoStorageException("Failed to create repository directory "+repoDirectory);
		}
		
		try {
			Git.init().setDirectory(repoDirectory).call().close();
		}
		catch (GitAPIException e) { 
			RepoStorageException t = new RepoStorageException("Failed to initialise git repository",e);
			try {
				FileUtil.deleteRecursive(repoDirectory);
			}
			catch (IOException e1) {
				t.addSuppressed(e1);
			}
			throw t;
		}

		RepoInfo r = new RepoInfo(repoId, taskId,usingTestingVersion,expiryDate);
		
		try (TransactionQueryRunner t = database.getQueryRunner()){
			r.insert(t);
			t.commit();
		} catch (SQLException e) {
			RepoStorageException t = new RepoStorageException("Failed to store repository details",e); 
			try {
				FileUtil.deleteRecursive(repoDirectory);
			} catch (IOException e1) {
				t.addSuppressed(e1);
			}
			throw t;
		}
		
		return new Repo(repoId,config, taskId,usingTestingVersion,expiryDate);
	}

	public String getRepoId() {
		return repoId;
	}

	public String getTaskId() {
		return taskId;
	}

	public boolean isUsingTestingVersion() {
		return usingTestingVersion;
	}

	public RepoInfo toRepoInfo() {
		return new RepoInfo(repoId,taskId,usingTestingVersion,expiryDate);
	}
	
	public boolean isExpired() {
		return new Date().after(expiryDate);
	}
	
}
