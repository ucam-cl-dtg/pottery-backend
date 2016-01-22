package uk.ac.cam.cl.dtg.teaching.pottery.dto;

import java.util.List;

import uk.ac.cam.cl.dtg.teaching.pottery.containers.StepResponse;

public class ValidationResponse implements StepResponse<List<ValidationStep>>{

		private List<ValidationStep> response;
		private boolean success;
		private String failMessage;
		
		public ValidationResponse() {
			super();
		}

		public ValidationResponse(String failMessage) {
			this.success = false;
			this.failMessage = failMessage;
		}
				
		public ValidationResponse(boolean success, List<ValidationStep> response, String failMessage) {
			super();
			this.response = response;
			this.success = success;
			this.failMessage = failMessage;
		}

		public boolean isSuccess() {
			return success;
		}

		public List<ValidationStep> getResponse() {
			return response;
		}

		public String getFailMessage() {
			return failMessage;
		}

}