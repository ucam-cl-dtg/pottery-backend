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
package uk.ac.cam.cl.dtg.teaching.pottery;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.commons.io.IOUtils;

public class FileUtil {

  /** Create the chosen directory (and any missing parent directories) if it doesn't exist. */
  public static void mkdirIfNotExists(File dir) throws IOException {
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        throw new IOException("Failed to create directory " + dir);
      }
    }
  }

  /** Delete everything in the given directory tree. */
  public static void deleteRecursive(File rootDirectory) throws IOException {
    if (!rootDirectory.exists()) {
      return;
    }
    File canonicalRoot = rootDirectory.getCanonicalFile();

    Files.walkFileTree(
        canonicalRoot.toPath(),
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (FileUtil.isParent(canonicalRoot, file.toFile())) {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            } else {
              throw new IOException("File not within parent directory");
            }
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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
   * <p>descendant is first converted to its canonical file. We then walk upwards. If we find the
   * parent file then we return true.
   *
   * @return true if descendant is a descendant of parent
   */
  public static boolean isParent(File parent, File descendant) throws IOException {
    descendant = descendant.getCanonicalFile();
    do {
      if (descendant.equals(parent)) {
        return true;
      }
    } while ((descendant = descendant.getParentFile()) != null);
    return false;
  }

  /**
   * Creates the specified directory and returns a Closeable object for use in try-with-resources
   * which will delete the whole tree.
   */
  public static AutoDelete mkdirWithAutoDelete(File dir) throws IOException {
    if (!dir.mkdirs()) {
      throw new IOException("Failed to create directory " + dir);
    }
    return new AutoDelete(dir);
  }

  /**
   * Copies all the files in sourceDir to destinationDir recursively and returns a list of
   * (relative) filenames copied.
   */
  public static ImmutableList<String> copyFilesRecursively(File sourceDir, File destinationDir)
      throws IOException {
    if (!sourceDir.exists()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<String> copiedFiles = ImmutableList.builder();
    Files.walkFileTree(
        sourceDir.toPath(),
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {

            File originalFile = file.toFile();
            Path localLocation = sourceDir.toPath().relativize(file);
            copiedFiles.add(localLocation.toString());
            File newLocation = destinationDir.toPath().resolve(localLocation).toFile();
            File newDir = newLocation.getParentFile();
            mkdirIfNotExists(newDir);
            try (FileOutputStream fos = new FileOutputStream(newLocation)) {
              try (FileInputStream fis = new FileInputStream(originalFile)) {
                IOUtils.copy(fis, fos);
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return copiedFiles.build();
  }

  public static class AutoDelete implements AutoCloseable {

    private boolean persist;
    private File parent;

    AutoDelete(File parent) {
      this.parent = parent;
      this.persist = false;
    }

    public void persist() {
      persist = true;
    }

    @Override
    public void close() throws IOException {
      if (!persist) {
        FileUtil.deleteRecursive(parent);
      }
    }
  }
}
