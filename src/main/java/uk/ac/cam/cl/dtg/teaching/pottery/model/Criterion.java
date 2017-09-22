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

import com.wordnik.swagger.annotations.ApiModel;
import java.util.HashMap;
import java.util.Map;
import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;

@ApiModel(
  value = "Criterion",
  description = "Describes a type of measurements which can be made of a user's code"
)
public class Criterion {

  private static final Map<String, String> UNITS = new HashMap<>();
  private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

  static {
    DESCRIPTIONS.put("compute", "Compute: The amount of CPU consumed by the solution.");
    UNITS.put("compute", Units.UNITLESS);

    DESCRIPTIONS.put(
        "complexity",
        "Complexity Class: How the runtime of  the program grows as the side of the input "
            + "changes.  big-O notation i.e. O(n^2).  This can be a more repeatable measurement "
            + "than just measuring runtime.  For some types of question there will be a variety of "
            + "solutions in different complexity classes.");
    UNITS.put("complexity", Units.UNIT_COMPLEXITYCLASS);
    // Time the execution of a parameterised test for various values for size of problem.  Curve fit
    // the resulting measurements to derive complexity class.  Consideration needs to be given to
    // the kind of data used in the parameterised test: for example a naïve quicksort will have
    // O(n^2) behaviour on pathological inputs.

    DESCRIPTIONS.put(
        "runningtime",
        "Running time: Runtime of the program for a set of benchmark inputs in comparison to some "
            + "set of standard solutions.  Some programs will admit a variety of solutions with "
            + "different levels of performance.  This measure is important in addition to "
            + "complexity class since for some problems there are still useful performance "
            + "improvements to make without being able to improve the complexity class.");
    UNITS.put("runningtime", Units.UNIT_MILLISECOND);
    // Measure the runtime of the program against a set of benchmark inputs.  Benchmark inputs need
    // to be chosen carefully to exploit all behaviours of the program.

    DESCRIPTIONS.put(
        "mempeak",
        "Peak Memory Usage: Peak memory usage is the largest working set required by the program. "
            + "This gives an idea of the amount of memory needed to execute the program");
    UNITS.put("mempeak", Units.UNIT_BYTES);
    // Peak Memory usage is reported through the ManagementFactory API for instrumentation agents on
    // a JVM

    DESCRIPTIONS.put(
        "memtotal",
        "Total Allocation: Every time memory is (heap) (de)allocated a cost is incurred. "
            + "Because of this some programs benefit from a statically allocated object pool.");
    UNITS.put("memtotal", Units.UNIT_BYTES);
    // At the moment it looks like the only way to do this is to use java instrumentation to rewrite
    // classes when loaded to count calls to new (or to rewrite constructors taking care for arrays
    // and superclasses).

    DESCRIPTIONS.put(
        "cache",
        "Cache Performance When data is fetched from memory is goes through the CPUs cache "
            + "hierarchy.  For some problems choosing an intelligent layout in memory will make "
            + "this use more efficient.  Efficiency in this case can be defined as the number of "
            + "cache hits the application gets – if you have a lot of hits then you are a) pulling "
            + "the data in such a way that its likely to be in the cache already b) not discarding "
            + "data you need from your cache");
    UNITS.put("cache", Units.UNIT_PERCENT);
    // This can be measured at the OS level.  Tools such as cachegrind can give counts of the number
    // of hits at different cache levels.  One useful value is the hit rate (%) which is the
    // percentage of memory accesses which resulted in a cache hit

    DESCRIPTIONS.put(
        "iototal",
        "I/O used The two major aspects of I/O are disk and network usage.  Many organisations "
            + "will be using a SAN and so disk usage also translates to network usage in many "
            + "cases.  At the moment the focus will be on disk I/O since questions involving "
            + "network programming will be considerably more difficult to deploy");
    UNITS.put("iototal", Units.UNIT_BYTES);
    // Total Usage Total amount of data read or written from disk.  Some problems will have
    // different usage depending on the apprach
    // This can be measured using the linux process accounting tools which can give per process
    // totals of the amount of data transferred.

    DESCRIPTIONS.put(
        "correctness",
        "Correctness of Code Correctness considers whether the code produces the correct "
            + "result/outcome rather than does it do so in a performant way");
    UNITS.put("correctness", Units.UNIT_BOOLEAN);

    DESCRIPTIONS.put(
        "robustness",
        "Robustness The tolerance of a solution to corner use cases that may lead to unstable "
            + "solutions or failure to complete a calculation. One example might be to consider "
            + "whether it works well for pathological inputs (e.g. sorted arrays for quicksort, "
            + "or really large floating point numbers)");
    UNITS.put("robustness", Units.UNIT_PERCENT);

    DESCRIPTIONS.put(
        "testability",
        "Testability What is the testability of the solution submitted in terms of the test "
            + "coverage provided by the test harnesses developed for all possible model answers to "
            + "the question.");
    UNITS.put("testability", Units.UNIT_PERCENT);
    // Don't know how to measure this automatically [JRR=> We talked about doing this by running
    // test coverage against all of the existing test cases that we have developed to test the
    // solution (i.e. undeststand the unit test coverage based on the unit tests that we have
    // developed for all possible solutions using the same tools and result metrics as the "Test
    // Coverage" item.]

    DESCRIPTIONS.put(
        "testcoverage",
        "Test Coverage How much test coverage do the tests they have written on their own code");
    UNITS.put("testcoverage", Units.UNIT_PERCENT);
    // Existing open source tools are available.  The most straightforward approach is to rewrite
    // the bytecode to include extra instructions to record execution.
  }

  private String type;
  private String unit;
  private String description;

  public Criterion() {
    super();
  }

  public Criterion(String type) throws CriterionNotFoundException {
    super();

    if (!UNITS.containsKey(type)) {
      throw new CriterionNotFoundException("Failed to recognise criterion " + type);
    }
    this.type = type;
    this.unit = UNITS.get(type);
    this.description = DESCRIPTIONS.get(type);
  }

  public Criterion(String type, String unit, String description) {
    super();
    this.type = type;
    this.unit = unit;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  class Units {
    public static final String UNIT_COMPLEXITYCLASS = "COMPLEXITYCLASS";
    public static final String UNIT_MILLISECOND = "MS";
    public static final String UNIT_BYTES = "BYTES";
    public static final String UNIT_PERCENT = "PERCENT";
    public static final String UNIT_BOOLEAN = "BOOLEAN";
    public static final String UNITLESS = "UNITLESS";
  }
}
