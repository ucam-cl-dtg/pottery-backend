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

package uk.ac.cam.cl.dtg.teaching.pottery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Interpretation;
import uk.ac.cam.cl.dtg.teaching.programmingtest.containerinterface.Measurement;

public class InterpretedMeasurement {

  private String criterion;
  private String measurement;
  private String result;
  private String explanation;

  @JsonCreator
  public InterpretedMeasurement(
      @JsonProperty("criterion") String criterion,
      @JsonProperty("measurement") String measurement,
      @JsonProperty("result") String result,
      @JsonProperty("explanation") String explanation) {
    super();
    this.criterion = criterion;
    this.measurement = measurement;
    this.result = result;
    this.explanation = explanation;
  }

  public InterpretedMeasurement(Measurement m, Map<String, Interpretation> imap) {
    super();
    this.criterion = m.getCriterion();
    this.measurement = m.getMeasurement();
    if (imap != null && imap.containsKey(m.getId())) {
      Interpretation i = imap.get(m.getId());
      this.result = i.getResult();
      this.explanation = i.getExplanation();
    }
  }

  public String getCriterion() {
    return criterion;
  }

  public String getMeasurement() {
    return measurement;
  }

  public String getResult() {
    return result;
  }

  public String getExplanation() {
    return explanation;
  }
}
