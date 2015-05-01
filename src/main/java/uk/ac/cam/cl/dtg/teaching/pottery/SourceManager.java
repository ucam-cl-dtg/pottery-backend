package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
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

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.RepoException;

public class SourceManager {

	public static final File REPO_ROOT = new File("/local/scratch/acr31/reporoot");
	public static final File TESTING_ROOT = new File("/local/scratch/acr31/testingroot");
	public static final String HEAD = "HEAD";
	
	private static Logger log = LoggerFactory.getLogger(SourceManager.class);

	private static final String WEBTAG_PREFIX="online-";
	
	public String createRepo() throws RepoException, IOException {
		
		Path repoDir = Files.createTempDirectory(REPO_ROOT.toPath(), "");
		
		String repoId = repoDir.getFileName().toString();
		try {
			Git.init().setDirectory(repoDir.toFile()).call().close();
		}
		catch (GitAPIException e) { 
			FileUtil.deleteRecursive(repoDir.toFile());
			throw new RepoException("Failed to initialise git repository",e); 
		}
		
		return repoId;
	}
	
	
	public void cloneForTesting(String repoId, String tag) throws IOException, RepoException {
		File newLocation = new File(TESTING_ROOT,repoId);
		if (newLocation.exists()) {
			FileUtil.deleteRecursive(newLocation);
		}
		if (!newLocation.mkdirs()) {
			throw new IOException("Failed to create testing directory "+newLocation);
		}
		try {
			Git.cloneRepository()
			.setURI(new File(REPO_ROOT,repoId).getPath())
			.setDirectory(newLocation)
			.setBranch(tag)
			.call()
			.close();
		} catch (GitAPIException e) {
			throw new RepoException("Failed to copy repository",e);
		}
	}
	
	
	public boolean existsTag(String repoId,String tag) throws IOException {
		Git git = Git.open(new File(REPO_ROOT,repoId));
		try {
			return git.getRepository().resolve(Constants.R_TAGS+tag) != null;
		}
		finally {
			git.close();
		}
	}
	
	public List<String> listFiles(String repoId, String tag) throws RepoException, IOException {
		Git git = Git.open(new File(REPO_ROOT,repoId));
		Repository repo = git.getRepository();
		RevWalk revWalk = new RevWalk(repo);
		
		RevTree tree = getRevTree(tag, repo, revWalk); 
		
		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		List<String> result = new LinkedList<String>();
		while(treeWalk.next()) {
			result.add(treeWalk.getNameString());
		}
		revWalk.dispose();
		repo.close();
		return result;
	}



	private RevTree getRevTree(String tag, Repository repo, RevWalk revWalk)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			IOException, RepoException, MissingObjectException {
		RevTree tree;
		try {
			ObjectId tagId = repo.resolve(HEAD.equals(tag) ? Constants.HEAD : Constants.R_TAGS+tag);
			if (tagId == null) {
				throw new RepoException("Failed to find tag "+tag);
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
			File repoDir = new File(REPO_ROOT,repoId);
			Git git = Git.open(repoDir);
			List<Ref> tagList;
			try {
				tagList = git.tagList().call();
			} catch (GitAPIException e) {
				throw new RepoException("Failed to list all tags in repo",e);
			}
			
			String prefix = Constants.R_TAGS+WEBTAG_PREFIX;
			int max = -1;
			for(Ref tag : tagList) {
				String tagName = tag.getName();
				if (tagName.startsWith(prefix)) {
					int value;
					try {
						value = Integer.parseInt(tagName.substring(prefix.length()));
					} catch (NumberFormatException e) {
						throw new RepoException("Failed to parse tag name "+tagName);						
					}
					if (value > max) max = value;
				}				
			}
			
			String newTag = WEBTAG_PREFIX + String.format("%03d", (max+1));
			
			try {
				git.tag().setName(newTag).call();
			} catch (GitAPIException e) {
				throw new RepoException("Failed to apply tag "+newTag+" to repo");
			}
						
			git.close();
			
			return newTag;
		}
	}
	
	public void updateFile(String repoId, String fileName, byte[] data) throws RepoException, IOException {
		synchronized (getMutex(repoId)) {
			File repoDir = new File(REPO_ROOT,repoId);
			File f = new File(repoDir,fileName);
			if (!FileUtil.isParent(REPO_ROOT, f)) {
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
		Git git = Git.open(new File(REPO_ROOT,repoId));
		Repository repo = git.getRepository();
		RevWalk revWalk = new RevWalk(repo);
		RevTree tree = getRevTree(tag, repo, revWalk); 
		
		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(true);
		treeWalk.setFilter(PathFilter.create(fileName));
		if(!treeWalk.next()) {
			throw new IOException("File not found");
		}
		
        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);
        
        return new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException,
					WebApplicationException {
				output.write(loader.getBytes());
				revWalk.dispose();
				repo.close();				
			}
        	
        };
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
