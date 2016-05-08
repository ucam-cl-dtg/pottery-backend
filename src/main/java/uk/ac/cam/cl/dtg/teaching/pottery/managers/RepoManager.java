package uk.ac.cam.cl.dtg.teaching.pottery.managers;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.RevertCommand;
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

import com.google.inject.Inject;
import com.google.inject.Singleton;

import uk.ac.cam.cl.dtg.teaching.pottery.Database;
import uk.ac.cam.cl.dtg.teaching.pottery.FileUtil;
import uk.ac.cam.cl.dtg.teaching.pottery.TransactionQueryRunner;
import uk.ac.cam.cl.dtg.teaching.pottery.app.Config;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.RepoInfo;
import uk.ac.cam.cl.dtg.teaching.pottery.dto.Task;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.NoHeadInRepoException;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

@Singleton
public class RepoManager {
	
	private File repoRoot;
	private File repoTestingRoot;
	private String headTag;
	private String webtagPrefix;
	
	private Database database;

	private static Logger log = LoggerFactory.getLogger(RepoManager.class);
	
	@Inject
	public RepoManager(Config config,Database database) {
		super();
		repoRoot = config.getRepoRoot();
		repoTestingRoot = config.getRepoTestingRoot();
		headTag = config.getHeadTag();
		webtagPrefix = config.getWebtagPrefix();
		this.database = database;
	}

	public String getHeadTag() { return headTag; }
	
	public RepoInfo getRepo(String repoId) throws RepoException {
		try (TransactionQueryRunner q = database.getQueryRunner()) {
			return RepoInfo.getByRepoId(repoId, q);
		} catch (SQLException e) {
			throw new RepoException("Failed to get repository",e);
		}
	}

	public RepoInfo createRepo(Task task) throws RepoException, IOException {
		
		Path repoDir = Files.createTempDirectory(repoRoot.toPath(), "");
		
		String repoId = repoDir.getFileName().toString();
		try {
			Git.init().setDirectory(repoDir.toFile()).call().close();
		}
		catch (GitAPIException e) { 
			FileUtil.deleteRecursive(repoDir.toFile());
			throw new RepoException("Failed to initialise git repository",e); 
		}
		
		RepoInfo r = new RepoInfo(repoId,task.getTaskId(),task.isReleased());
		try (TransactionQueryRunner t = database.getQueryRunner()){
			r.insert(t);
			t.commit();
		} catch (SQLException e) {
			RepoException t = new RepoException("Failed to store repository details",e); 
			try {
				FileUtil.deleteRecursive(repoDir.toFile());
			} catch (IOException e1) {
				t.addSuppressed(e1);
			}
			throw t;
		}
		return r;
	}
	
	/**
	 * Recursively copy the files from the sourceLocation to the chosen repo. Add them and commit them to the repo.
	 * 
	 * @param repoId
	 * @param sourceLocation
	 * @throws IOException
	 * @throws RepoException 
	 */
	public void copyFiles(String repoId, File sourceLocation) throws IOException, RepoException {
		List<String> copiedFiles = new LinkedList<>();
		
		File repoDir = new File(repoRoot,repoId);
		
		Files.walkFileTree(sourceLocation.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				
				File originalFile = file.toFile();
				Path localLocation = sourceLocation.toPath().relativize(file);
				copiedFiles.add(localLocation.toString());
				File newLocation = repoDir.toPath().resolve(localLocation).toFile();
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
		
		Git git = Git.open(repoDir);
		try {
			for(String f : copiedFiles) {
				git.add().addFilepattern(f).call();
			}
			git.commit().setMessage("Copied files").call();
		} catch (GitAPIException e) {
			try {
				git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
				throw new RepoException("Failed to commit update after copying files. Rolled back",e);
			} catch (GitAPIException e1) {
				e1.addSuppressed(e);
				throw new RepoException("Failed to rollback failed update",e1);
			}
		}
		
		
	}
	
	
	public File cloneForTesting(String repoId, String tag) throws IOException, RepoException {
		File newLocation = new File(repoTestingRoot,repoId);
		if (newLocation.exists()) {
			FileUtil.deleteRecursive(newLocation);
		}
		if (!newLocation.mkdirs()) {
			throw new IOException("Failed to create testing directory "+newLocation);
		}
		try {
			Git.cloneRepository()
			.setURI(new File(repoRoot,repoId).getPath())
			.setDirectory(newLocation)
			.setBranch(tag)
			.call()
			.close();
		} catch (GitAPIException e) {
			throw new RepoException("Failed to copy repository",e);
		}
		return newLocation;
	}
	
	
	public boolean existsTag(String repoId,String tag) throws IOException {
		try (Git git = Git.open(new File(repoRoot,repoId))) {
			return git.getRepository().resolve(Constants.R_TAGS+tag) != null;
		}
	}
	
	public List<String> listFiles(String repoId, String tag) throws RepoException, IOException {
		try (Git git = Git.open(new File(repoRoot,repoId))) {
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
		}
	}



	private RevTree getRevTree(String tag, Repository repo, RevWalk revWalk)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			IOException, RepoException, MissingObjectException, NoHeadInRepoException {
		RevTree tree;
		try {
			ObjectId tagId = repo.resolve(headTag.equals(tag) ? Constants.HEAD : Constants.R_TAGS+tag);
			if (tagId == null) {
				if (headTag.equals(tag)) {
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

	public String newTag(String repoId) throws IOException, RepoException {
		synchronized (getMutex(repoId)) {	
			File repoDir = new File(repoRoot,repoId);
			try (Git git = Git.open(repoDir)) {
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
		}
	}

	public List<String> listTags(String repoId) throws RepoException, IOException {
		String prefix = Constants.R_TAGS+webtagPrefix;
		synchronized (getMutex(repoId)) {
			File repoDir = new File(repoRoot,repoId);
			try (Git g = Git.open(repoDir)) {
				return g.tagList().call().stream().
						filter(t -> t.getName().startsWith(prefix)).
						map(t -> t.getName().substring(Constants.R_TAGS.length())).
						collect(Collectors.toList());
			}
			catch (GitAPIException e) {
				throw new RepoException("Failed to get tag list",e);
			}
		}
	}
	
	/**
	 * Set the contents of the repository to be the same as at the particular tag. Note that this does a git revert and not a reset.
	 * 
	 * @param repoId
	 * @param tag
	 * @throws IOException
	 * @throws RepoException
	 */
	public void reset(String repoId, String tag) throws IOException, RepoException {
		synchronized (getMutex(repoId)) {
			try (Git git = Git.open(new File(repoRoot,repoId))) {
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
				catch (GitAPIException e) {
					throw new RepoException("Failed to reset repo to tag "+tag,e);
				}
			}
		}
	
	
	public void deleteFile(String repoId, String fileName) throws IOException, RepoException {
		synchronized (getMutex(repoId)) {
			try (Git git = Git.open(new File(repoRoot,repoId))) {
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
		}
	}

	
	public void updateFile(String repoId, String fileName, byte[] data) throws RepoException, IOException {
		synchronized (getMutex(repoId)) {
			File repoDir = new File(repoRoot,repoId);
			File f = new File(repoDir,fileName);
			if (!FileUtil.isParent(repoRoot, f)) {
				throw new IOException("Invalid fileName "+fileName);
			}
			if (f.isDirectory()) {
				throw new IOException("File already exists and is a directory");
			}
			
			File parentFile = f.getParentFile();
			
			if (!parentFile.exists()) {
				if (!parentFile.mkdirs()) {
					throw new IOException("Failed to create directories for "+fileName);
				}
			}
			try(FileOutputStream fos = new FileOutputStream(f)) {
				IOUtils.write(data, fos);
			}
			Git git = Git.open(repoDir);
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
	}
	
	public StreamingOutput readFile(String repoId, String tag, String fileName) throws IOException, RepoException {
		try (Git git = Git.open(new File(repoRoot,repoId))) {
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
  	}
	
	private Map<String,Object> repoLocks = new ConcurrentHashMap<String, Object>();
	public Object getMutex(String repoId) {
		Object mutex = repoLocks.get(repoId);
		if (mutex == null) {
			synchronized (repoLocks) {
				mutex = repoLocks.get(repoId);
				if (mutex == null) {
					mutex = new Object();
					repoLocks.put(repoId, mutex);
				}
			}
		}
		return mutex;
	}
	
}
