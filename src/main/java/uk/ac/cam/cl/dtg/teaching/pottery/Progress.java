/*
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

package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.LinkedList;
import java.util.List;
import uk.ac.cam.cl.dtg.teaching.pottery.model.Submission;

/**
 * Class for tracking the progress that a candidate has made through the tests. Keeps track of all
 * their submissions, best results etc.
 *
 * @author acr31
 */
public class Progress {

  private String progressId;
  private String taskId;

  private List<Submission> submissions;

  public Progress() {
    submissions = new LinkedList<Submission>();
  }

  public String getProgressId() {
    return progressId;
  }

  public void setProgressId(String progressId) {
    this.progressId = progressId;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public void addSubmission(Submission submission) {
    submissions.add(submission);
  }
}
