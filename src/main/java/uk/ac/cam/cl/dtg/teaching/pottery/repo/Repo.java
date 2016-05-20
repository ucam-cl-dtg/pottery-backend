package uk.ac.cam.cl.dtg.teaching.pottery.repo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
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

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.NoHeadInRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

public class Repo {
	
	private static Logger log = LoggerFactory.getLogger(Repo.class);

	private String repoId;
	
	private String taskId;
	
	private boolean released;
	
	/**
	 * The directory holding this repository. This object is also used as a mutex protecting concurrent modification.
	 */
	private File repoDirectory;

	private File repoTestingDirectory;

	private String webtagPrefix;
	
	private Repo(String repoId, Config c, String taskId, boolean released) {
		this.repoId = repoId;
		this.repoDirectory = new File(c.getRepoRoot(),repoId);
		this.repoTestingDirectory = new File(c.getRepoTestingRoot(),repoId);
		this.webtagPrefix = c.getWebtagPrefix();
		this.taskId = taskId;
		this.released = released;
	}

	/** 
	 * Recursively copy all files from this sourceLocation and add them to the repository 
	 * @param sourceLocation the location to copy from
	 * @throws RepoException
	 */
	public void copyFiles(File sourceLocation) throws RepoException {
		synchronized (repoDirectory) {
			List<String> copiedFiles = new LinkedList<>();
			
			try (Git git = Git.open(repoDirectory)) {
				try {
					Files.walkFileTree(sourceLocation.toPath(), new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file,
								BasicFileAttributes attrs) throws IOException {
							
							File originalFile = file.toFile();
							Path localLocation = sourceLocation.toPath().relativize(file);
							copiedFiles.add(localLocation.toString());
							File newLocation = repoDirectory.toPath().resolve(localLocation).toFile();
							File newDir = newLocation.getParentFile();
							log.debug("Copying {} to {}",originalFile, newLocation);
	
							if (newDir.exists()) {
								newDir.mkdirs();
							}
							
							try(FileOutputStream fos = new FileOutputStream(newLocation)) {
								try(FileInputStream fis = new FileInputStream(originalFile)) {
									IOUtils.copy(fis, fos);
								}
							}
							return FileVisitResult.CONTINUE;
						}
					});
			
					for(String f : copiedFiles) {
						git.add().addFilepattern(f).call();
					}
					git.commit().setMessage("Copied files").call();
				} catch (IOException | GitAPIException e) {
					try {
						git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
						throw new RepoException("An error occurred when copying and adding to repository. Rolled back",e);
					} catch (GitAPIException e1) {
						e1.addSuppressed(e);
						throw new RepoException("Failed to rollback failed update",e1);
					}
				}
			} catch (IOException e2) {
				throw new RepoException("Failed to open repository",e2);
			}
		}		
	}
	
	/**
	 * Update the checkout of this repo that we use for testing to point to a new tag
	 * 
	 * @param tag the tag to update the test to point to
	 * @throws RepoException if something goes wrong
	 */
	public void setVersionToTest(String tag) throws RepoException {
		synchronized (repoTestingDirectory) {
			if (repoTestingDirectory.exists()) {
				try {
					FileUtil.deleteRecursive(repoTestingDirectory);
				} catch (IOException e) {
					throw new RepoException("Failed to delete previous testing directory",e);
				}
			}
			if (!repoTestingDirectory.mkdirs()) {
				throw new RepoException("Failed to create directory for holding test checkout");
			}
		
			try {
				Git.cloneRepository()
				.setURI(repoDirectory.getPath())
				.setDirectory(repoTestingDirectory)
				.setBranch(tag)
				.call()
				.close();
			} catch (GitAPIException e) {
				throw new RepoException("Failed to clone repository",e);
			}
		}
	}

	
	/**
	 * Check if tag is defined in this repository
	 * @param tag to check for
	 * @return true if tag exists
	 * @throws RepoException if something goes wrong
	 */
	public boolean existsTag(String tag) throws RepoException {
		try (Git git = Git.open(repoDirectory)) {
			return git.getRepository().resolve(Constants.R_TAGS+tag) != null;
		} catch (IOException e) {
			throw new RepoException("Failed to lookup tag in repository "+repoId,e);
		}
	}
	

	/**
	 * List the files in this repo tagged with a particular tag
	 * @param tag the tag of interest
	 * @return a list of file names relative to the root of the repository
	 * @throws RepoException
	 */
	public List<String> listFiles(String tag) throws RepoException {
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
						result.add(treeWalk.getNameString());
					}
				}
				revWalk.dispose();
				return result;
			} catch (NoHeadInRepoException e) {
				return new LinkedList<String>();
			} 
		} catch (IOException e) {
			throw new RepoException("Failed to access files in repository "+repoId+" under tag "+tag,e);
		}
		
	}

	
	/**
	 * Create a new tag in this repository
	 * @return the name of the tag
	 * @throws RepoException
	 */
	public String createNewTag() throws RepoException {
		synchronized (repoDirectory) {	
			try (Git git = Git.open(repoDirectory)) {
				List<Ref> tagList;
				try {
					tagList = git.tagList().call();
				} catch (GitAPIException e) {
					throw new RepoException("Failed to list all tags in repo",e);
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
							throw new RepoException("Failed to parse tag name "+tagName,e);						
						}
						if (value > max) max = value;
					}				
				}
				
				String newTag = webtagPrefix + String.format("%03d", (max+1));
				
				try {
					git.tag().setName(newTag).call();
				} catch (GitAPIException e) {
					throw new RepoException("Failed to apply tag "+newTag+" to repo",e);
				}
				return newTag;
			} 
			catch (IOException e) {
				throw new RepoException("Failed to open repository "+repoId,e);
			}
		}
	}

	/**
	 * List all tags in this repository (only tags which have the webtag prefix are returned
	 * @return a list of tag names
	 * @throws RepoException if something goes wrong
	 */
	public List<String> listTags() throws RepoException {
		String prefix = Constants.R_TAGS+webtagPrefix;
		synchronized (repoDirectory) {
			try (Git g = Git.open(repoDirectory)) {
				return g.tagList().call().stream().
						filter(t -> t.getName().startsWith(prefix)).
						map(t -> t.getName().substring(Constants.R_TAGS.length())).
						collect(Collectors.toList());
			}
			catch (IOException|GitAPIException e) {
				throw new RepoException("Failed to get tag list",e);
			}
		}
	}


	/**
	 * Set the contents of the repository to be the same as at the particular tag. Note that this does a git revert and not a reset.
	 * 
	 * @param tag the tag to reset to
	 * @throws RepoException if something goes wrong
	 */
	public void reset(String tag) throws RepoException {
		synchronized (repoDirectory) {
			try (Git git = Git.open(repoDirectory)) {
				Repository r = git.getRepository();
				Ref tagRef = r.findRef(tag);
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
				throw new RepoException("Failed to reset repo to tag "+tag,e);
			}
		}
	}
	

	/**
	 * Delete the file specified
	 * @param fileName relative to the root of the repository
	 * @throws RepoException if we fail to delete the file
	 */
	public void deleteFile(String fileName) throws RepoException {
		synchronized (repoDirectory) {
			try (Git git = Git.open(repoDirectory)) {
				try {
					git.rm().addFilepattern(fileName).call();
					git.commit().setMessage("Removing file: "+fileName).call();
				}
				catch (GitAPIException e) {
					try {
						git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
						throw new RepoException("Failed to commit delete of "+fileName+". Rolled back",e);
					} catch (GitAPIException e1) {
						e1.addSuppressed(e);
						throw new RepoException("Failed to rollback delete of "+fileName,e1);
					}
				}
			}
			catch (IOException e) {
				throw new RepoException("Failed to open repository "+repoId,e);
			}
		}
	}

	
	/**
	 * Replace the contents of a file
	 * @param fileName the filename with path relative to the root of the repository
	 * @param data to replace the contents of the file with
	 * @throws RepoException
	 */
	public void updateFile(String fileName, byte[] data) throws RepoException {
		synchronized (repoDirectory) {
			File f = new File(repoDirectory,fileName);
			try {
				if (!FileUtil.isParent(repoDirectory, f)) {
					throw new RepoException("Invalid fileName "+fileName);
				}
			} catch (IOException e) {
				throw new RepoException("Failed to perform security check on requested filename",e);
			}
			
			if (f.isDirectory()) {
				throw new RepoException("File already exists and is a directory");
			}
			
			File parentFile = f.getParentFile();
			
			if (!parentFile.exists()) {
				if (!parentFile.mkdirs()) {
					throw new RepoException("Failed to create directories for "+fileName);
				}
			}
			try(FileOutputStream fos = new FileOutputStream(f)) {
				IOUtils.write(data, fos);
			} 
			catch (IOException e) {
				throw new RepoException("Failed to write data to file "+fileName,e);
			}
			try (Git git = Git.open(repoDirectory)) {
				try {
					git.add().addFilepattern(fileName).call();
					git.commit().setMessage("Updating file "+fileName).call();
				} catch (GitAPIException e) {
					try {
						git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
						throw new RepoException("Failed to commit update to "+fileName+". Rolled back",e);
					} catch (GitAPIException e1) {
						e1.addSuppressed(e);
						throw new RepoException("Failed to rollback failed update to "+fileName,e1);
					}
				}
			}
			catch (IOException e) {
				throw new RepoException("Failed to open repository",e);
			}
		}
	}

	
	/**
	 * Read the contents of a particular file at a particular version
	 * @param tag the tag of the version to use or HEAD
	 * @param fileName the filename relative to the root of the repository
	 * @return a StreamingOutput instance containing the data
	 * @throws RepoException if something goes wrong
	 */
	public StreamingOutput readFile(String tag, String fileName) throws RepoException {
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
			throw new RepoException("Failed to read file from repository",e);
		}
  	}
	

	
	
	private RevTree getRevTree(String tag, Repository repo, RevWalk revWalk)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			IOException, RepoException, MissingObjectException, NoHeadInRepoException {
		RevTree tree;
		try {
			ObjectId tagId = repo.resolve(Constants.HEAD.equals(tag) ? Constants.HEAD : Constants.R_TAGS+tag);
			if (tagId == null) {
				if (Constants.HEAD.equals(tag)) {
					throw new NoHeadInRepoException("Failed to find HEAD in repo.");
				}
				else {
					throw new RepoException("Failed to find tag "+tag);
				}
			}
			RevCommit revCommit = revWalk.parseCommit(tagId);
			tree = revCommit.getTree();

		} catch (RevisionSyntaxException e) {
			throw new RepoException("Failed to load revision for head of repository",e);
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
	 * @throws RepoException if the repository does not exist or if it can't be opened
	 */
	static Repo openRepo(String repoId, Config config, Database database) throws RepoException {
		
		File repoDirectory = new File(config.getRepoRoot(),repoId);
		
		if (!repoDirectory.exists()) {
			throw new RepoException("Failed to find repository directory "+repoDirectory);
		}
		
		try(TransactionQueryRunner q = database.getQueryRunner()) {
			RepoInfo r = RepoInfo.getByRepoId(repoId, q);
			if (r != null) {
				return new Repo(repoId,config, r.getTaskId(),r.isRelease());
			}
			else {
				throw new RepoException("Repository with ID "+repoId+" does not exist in database");
			}			
		}
		catch (SQLException e) {
			throw new RepoException("Failed to lookup repository with ID "+repoId+" in database",e);
		}
		
	}
	
	/**
	 * Create a new repository and return an appropriate repo object. Use RepoFactory rather than calling this method directly
	 * 
	 * @param repoId the ID of the repo to open
	 * @param task the task associated with this repo ID
	 * @param config server configuration
	 * @param database database connection
	 * @return a repo object for this repository
	 * @throws RepoException if the repository couldn't be created
	 */
	static Repo createRepo(String repoId, Task task, Config config, Database database) throws RepoException {
		
		File repoDirectory = new File(config.getRepoRoot(),repoId);
		
		if (!repoDirectory.mkdir()) {
			throw new RepoException("Failed to create repository directory "+repoDirectory);
		}
		
		try {
			Git.init().setDirectory(repoDirectory).call().close();
		}
		catch (GitAPIException e) { 
			RepoException t = new RepoException("Failed to initialise git repository",e);
			try {
				FileUtil.deleteRecursive(repoDirectory);
			}
			catch (IOException e1) {
				t.addSuppressed(e1);
			}
			throw t;
		}

		RepoInfo r = new RepoInfo(repoId, task.getTaskId(), task.isReleased());
		
		try (TransactionQueryRunner t = database.getQueryRunner()){
			r.insert(t);
			t.commit();
		} catch (SQLException e) {
			RepoException t = new RepoException("Failed to store repository details",e); 
			try {
				FileUtil.deleteRecursive(repoDirectory);
			} catch (IOException e1) {
				t.addSuppressed(e1);
			}
			throw t;
		}
		
		return new Repo(repoId,config, task.getTaskId(),task.isReleased());
	}

	public String getRepoId() {
		return repoId;
	}

	public String getTaskId() {
		return taskId;
	}

	public boolean isReleased() {
		return released;
	}

	public File getTestingDirectory() {
		return repoTestingDirectory;
	}

	public RepoInfo toRepoInfo() {
		return new RepoInfo(repoId,taskId,released);
	}
	
}
