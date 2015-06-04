package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.HashMap;
import java.util.Map;

import uk.ac.cam.cl.dtg.teaching.pottery.exceptions.CriterionNotFoundException;


public class Criterion {

	private static final Map<String,String> CRITERIA = new HashMap<>();
	static {
		/**
		 * Compute: The amount of CPU consumed by the solution. 
		 */
		CRITERIA.put("compute",Units.UNITLESS);

		/**
		 * Complexity Class:	How the runtime of  the program grows as the side of the input changes.  big-O notation i.e. O(n^2).  This can be a more repeatable measurement than just measuring runtime.  For some types of question there will be a variety of solutions in different complexity classes.
		 *
		 * Time the execution of a parameterised test for various values for size of problem.  Curve fit the resulting measurements to derive complexity class.  Consideration needs to be given to the kind of data used in the parameterised test: for example a naïve quicksort will have O(n^2) behaviour on pathological inputs.
		 */
		CRITERIA.put("complexity",Units.UNIT_COMPLEXITYCLASS);

		/**
		 * Running time: Runtime of the program for a set of benchmark inputs in comparison to some set of standard solutions.  Some programs will admit a variety of solutions with different levels of performance.  This measure is important in addition to complexity class since for some problems there are still useful performance improvements to make without being able to improve the complexity class. 
		 *
		 * Measure the runtime of the program against a set of benchmark inputs.  Benchmark inputs need to be chosen carefully to exploit all behaviours of the program.	
		 */
		CRITERIA.put("runningtime",Units.UNIT_MILLISECOND);

		/**
		 * Peak Memory Usage:	Peak memory usage is the largest working set required by the program.  This gives an idea of the amount of memory needed to execute the program
		 *
		 * Peak Memory usage is reported through the ManagementFactory API for instrumentation agents on a JVM
		 */
		CRITERIA.put("mempeak",Units.UNIT_BYTES);
		
		/**
		 * Total Allocation: Every time memory is (heao) (de)allocated a cost is incurred.  Because of this some programs benefit from a statically allocated object pool.
		 *
		 * At the moment it looks like the only way to do this is to use java instrumentation to rewrite classes when loaded to count calls to new (or to rewrite constructors taking care for arrays and superclasses).
		 */
		CRITERIA.put("memtotal",Units.UNIT_BYTES);

		/**
		 * Access Patterns	The order in which data is loaded from memory has a big impact on performance.  This is difficult to measure directly and so its probably easier to measure cache performance instead – good access patterns will have fewer cache misses
		 *
		 * One complicated method would be to use the kernel page table to trigger interrupts on each memory access.  This would require OS modification and so is very invasive.  Its possible that useful information could be gained through the java tools interface looking at the allocation patterns of objects but its not clear how this would be distilled into a meaningul value to compare between tests
		 */

		/**
		 * Cache Performance	When data is fetched from memory is goes through the CPUs cache hierachy.  For some problems choosing an intelligent layout in memory will make this use more efficient.  Efficiency in this case can be defined as the number of cache hits the application gets – if you have a lot of hits then you are a) pulling the data in such a way that its likely to be in the cache already b) not discarding data you need from your cache
		 *
		 * This can be measured at the OS level.  Tools such as cachegrind can give counts of the number of hits at different cache levels.  One useful value is the hit rate (%) which is the percentage of memory accesses which resulted in a cache hit
		 */
		CRITERIA.put("cache", Units.UNIT_PERCENT);
		
		/**
		 * I/O used	The two major aspects of I/O are disk and network usage.  Many organisations will be using a SAN and so disk usage also translates to network usage in many cases.  At the moment the focus will be on disk I/O since questions involving network programming will be considerably more difficult to deploy 
		 * Total Usage	Total amount of data read or written from disk.  Some problems will have different usage depending on the apprach
		 * 
		 * This can be measured using the linux process accounting tools which can give per process totals of the amount of data transferred.
		 */
		CRITERIA.put("iototal", Units.UNIT_BYTES);
		
		/**
		 * Access Pattern	For spinning media burst reads are better than short reads with seeks, for all kinds of media there are caches in the filesystem hierachy.  Making effective use of these can have a big performance impact on a program 	
		 *
		 * This could perhaps be measured using LD_PRELOAD to intercept calls to open and read and keep track of read locations.  This will be complicated to get right.
		 */
		
		/**
		 * Correctness of Code	Correctness considers whether the code produces the correct result/outcome rather than does it do so in a performant way
		 *
		 * Tests whether the code computes the correct results
		 */
		CRITERIA.put("correctness", Units.UNIT_BOOLEAN);
		
		/**
		 * Robustness	The tolerance of a solution to corner use cases that may lead to unstable solutions or failure to complete a calculation. One example might be to consider whether it works well for pathological inputs (e.g. sorted arrays for quicksort, or really large floating point numbers) 
		 *
		 * Set of tests providing these difficult inputs. Report the percentage of these which pass
		 */
		CRITERIA.put("robustness", Units.UNIT_PERCENT);
		
		/**
		 * Testability	What is the testability of the solution submitted in terms of the test coverage provided by the test harnesses developed for all possible model answers to the question.
		 *
		 * Don't know how to measure this automatically [JRR=> We talked about doing this by running test coverage against all of the existing test cases that we have developed to test the solution (i.e. undeststand the unit test coverage based on the unit tests that we have developed for all possible solutions using the same tools and result metrics as the "Test Coverage" item.]
		 */
		CRITERIA.put("testability", Units.UNIT_PERCENT);
		
		/**
		 * Test Coverage	How much test coverage do the tests they have written on their own code
		 *
		 * Existing open source tools are available.  The most straightforward approach is to rewrite the bytecode to include extra instructions to record execution.
		 */
		CRITERIA.put("testcoverage", Units.UNIT_PERCENT);
	}
	
	
	
	
	class Units {
		public static final String UNIT_COMPLEXITYCLASS = "COMPLEXITYCLASS";
		public static final String UNIT_MILLISECOND = "MS";
		public static final String UNIT_BYTES = "BYTES";
		public static final String UNIT_PERCENT = "PERCENT";
		public static final String UNIT_BOOLEAN = "BOOLEAN";
		public static final String UNITLESS = "UNITLESS";
	}

	private String type;

	private String unit;

	public Criterion(String type) throws CriterionNotFoundException {
		super();
		
		if (!CRITERIA.containsKey(type)) {
			throw new CriterionNotFoundException("Failed to recognise criterion "+type);
		}
		this.type = type;
		this.unit = CRITERIA.get(type);
	}
	
	public Criterion(String type, String unit) {
		super();
		this.type = type;
		this.unit = unit;
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


}
