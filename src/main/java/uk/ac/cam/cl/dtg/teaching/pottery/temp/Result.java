package uk.ac.cam.cl.dtg.teaching.pottery.temp;

import java.util.List;


public class Result {

		private List<ValidationStep> results;
		private String status;
		private String diagnosticMessage;
		
		public Result() {
			super();
		}

		public List<ValidationStep> getResults() {
			return results;
		}

		public void setResults(List<ValidationStep> results) {
			this.results = results;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getDiagnosticMessage() {
			return diagnosticMessage;
		}

		public void setDiagnosticMessage(String diagnosticMessage) {
			this.diagnosticMessage = diagnosticMessage;
		}
		
		
	
}