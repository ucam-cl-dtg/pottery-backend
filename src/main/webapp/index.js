$(document).ready(
		function() {

			var submissionId = 0;
			
			var reportSuccess = function(result) {
				$("#assignmentsList pre").text(JSON.stringify(result,undefined,2));
				$("#error pre").text("");
			}

			var reportError = function(xhr) {
				$("#assignmentsList pre").text("Error");
				$("#error pre").text(JSON.stringify($.parseJSON(xhr.responseText),undefined,2));
			}

			
			$("#listTasksButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks",
							success : function(result) {
								reportSuccess(result);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});

			$("#startTaskForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/tasks',
					type: 'POST',
					data: {"taskId" : $("#startTaskTaskId").val() },
					success: function (result) {
						reportSuccess(result);
						$("input[name='progressId']").val(result.progressId);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;

			});
			
			
			$("#submitForm").submit(function(event) {
				event.preventDefault();
				var formData = new FormData($(this)[0]);

				$.ajax({
					url: 'api/progress/'+$("#submitFormProgressId").val(),
					type: 'POST',
					data: formData,
					cache: false,
					contentType: false,
					processData: false,
					success: function (result) {
						reportSuccess(result);
						$("input[name='submissionId']").val(result.submissionId);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;
			});

			$("#requestTestForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/submissions/' + $("#requestTestSubmissionId").val(),
					type: 'POST',
					data: {},
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});
				return false;
			});

			$("#pollSubmissionForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/submissions/' + $("#pollSubmissionSubmissionId").val(),
					type: 'GET',
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});
				return false;
			});


			$("#pollStatusForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/submissions/' + $("#statusSubmissionId").val()+"/status",
					type: 'GET',
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});
				return false;
			});

			$("#getResultForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/submissions/' + $("#getResultSubmissionId").val()+"/result",
					type: 'GET',
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});
				return false;
			});

		});