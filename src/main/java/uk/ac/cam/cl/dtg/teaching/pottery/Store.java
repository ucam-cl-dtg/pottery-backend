package uk.ac.cam.cl.dtg.teaching.pottery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.google.common.collect.ImmutableMap;

public class Store {

	public static Map<String,Task> tasks = ImmutableMap.of(
			"3d0e6004-71fa-42b4-a304-d7236e0066a3",
			new Task("3d0e6004-71fa-42b4-a304-d7236e0066a3","<h1>String reverse</h1><p>The function <texttt>reverse</texttt> should take an array of characters and reverse their order storing the result in the original array.  Complete the implementation of this function.</p>"),
			"133fd4db-905d-4b09-a37f-abb4fb463d2a",
			new Task("133fd4db-905d-4b09-a37f-abb4fb463d2a","<h1>Averages</h1><p>The function <texttt>mean</texttt> should take an array of doubles and compute their mean. Complete the implementation of this function.</p>")			
			);
	
	public static Map<String,Progress> progress = new ConcurrentHashMap<String, Progress>();
	
	public static Map<String,Submission> submission = new ConcurrentHashMap<String,Submission>();
	
	public static ConcurrentLinkedDeque<Submission> testingQueue = new ConcurrentLinkedDeque<Submission>();
	
}
