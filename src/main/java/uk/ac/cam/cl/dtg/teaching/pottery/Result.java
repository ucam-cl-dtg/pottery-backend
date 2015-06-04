package uk.ac.cam.cl.dtg.teaching.pottery;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The results of the testing
 * @author acr31
 *
 */
public class Result {

	private String result;
	
	private ObjectMapper o = new ObjectMapper();
	
	private uk.ac.cam.cl.dtg.teaching.pottery.temp.Result validationResult;

	private List<uk.ac.cam.cl.dtg.teaching.pottery.temp.HarnessStep> harnessResult;
	
	public Result() {
		super();
		try {
			validationResult = o.readValue(
					"{\"results\":[{\"criterion\":\"correctness\",\"result\":\"true\",\"references\":[\"average0\",\"average1\"]},{\"criterion\":\"compute\",\"result\":\"930\",\"references\":[\"compute0\",\"compute1\",\"compute2\",\"compute3\",\"compute4\",\"compute5\",\"compute6\",\"compute7\",\"compute8\",\"compute9\"]},{\"criterion\":\"complexity\",\"result\":\"o(n)\",\"references\":[\"compute0\",\"compute1\",\"compute2\",\"compute3\",\"compute4\",\"compute5\",\"compute6\",\"compute7\",\"compute8\",\"compute9\"]}],\"status\":\"COMPLETED\",\"diagnosticMessage\":null}", 
					uk.ac.cam.cl.dtg.teaching.pottery.temp.Result.class);
			harnessResult = o.readValue("[{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"Resulting array\",\"actual\":1.0,\"id\":\"average0\"},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"Resulting array\",\"actual\":2.0,\"id\":\"average1\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 10000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":40,\"id\":\"compute0\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 20000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":40,\"id\":\"compute1\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 30000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":40,\"id\":\"compute2\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 40000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":60,\"id\":\"compute3\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 50000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":90,\"id\":\"compute4\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 60000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":110,\"id\":\"compute5\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 70000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":120,\"id\":\"compute6\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 80000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":130,\"id\":\"compute7\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 90000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":150,\"id\":\"compute8\"},{\"type\":\"MESSAGE\",\"message\":\"Building array with 100000000 elements\",\"actual\":null,\"id\":null},{\"type\":\"MESSAGE\",\"message\":\"Called public static double Average.mean(double[])\",\"actual\":null,\"id\":null},{\"type\":\"MEASUREMENT\",\"message\":\"CPU usage\",\"actual\":160,\"id\":\"compute9\"}]", new TypeReference<List<uk.ac.cam.cl.dtg.teaching.pottery.temp.HarnessStep>>() {});
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error(e);
		}
			
	}
	
	// list of measurements
	
	// report where each item is possibly associated with either a submission, a file, a range of lines or a line
	
	
	
	public String getResult() {
		return result;
	}



	public void setResult(String result) {
		this.result = result;
	}

	public uk.ac.cam.cl.dtg.teaching.pottery.temp.Result getValidationResult() {
		return validationResult;
	}

	public void setValidationResult(
			uk.ac.cam.cl.dtg.teaching.pottery.temp.Result validationResult) {
		this.validationResult = validationResult;
	}

	public List<uk.ac.cam.cl.dtg.teaching.pottery.temp.HarnessStep> getHarnessResult() {
		return harnessResult;
	}

	public void setHarnessResult(
			List<uk.ac.cam.cl.dtg.teaching.pottery.temp.HarnessStep> harnessResult) {
		this.harnessResult = harnessResult;
	}

	

}
