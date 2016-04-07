package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

public class FileUtil {

	public static void deleteRecursive(final File dir) throws IOException {
		Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				if (FileUtil.isParent(dir, file.toFile())) {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				} else {
					throw new IOException("File not within parent directory");
				}
			}
	
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc)
					throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
	
		});
	}

	/**
	 * Check whether one file is a descendant of the other.  
	 * 
	 * descendant is first converted to its canonical file.  We then walk upwards.  If we find the parent file then we return true.
	 * @param parent
	 * @param descendant
	 * @return true if descendant is a descendant of parent
	 * @throws IOException
	 */
	public static boolean isParent(File parent, File descendant) throws IOException {
		descendant = descendant.getCanonicalFile();
		do {
			if (descendant.equals(parent))
				return true;
		} while ((descendant = descendant.getParentFile()) != null);
		return false;
	}
	
	private static Object createLock = new Object();
	public static String createNewDirectory(File parent) throws IOException {
		synchronized (createLock) {
			while(true)	{
				String name = UUID.randomUUID().toString();
				File dir = new File(parent,name);
				if (!dir.exists()) {
					if (!dir.mkdir()) {
						throw new IOException("Failed to create directory "+dir.toString());
					}
					return name;
				}
			}
		}
	}

}
