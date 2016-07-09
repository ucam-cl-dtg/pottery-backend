$(document).ready(
		function() {

			var submissionId = 0;
			
			var reportSuccess = function(result) {
//				$("#assignmentsList pre").text(JSON.stringify(result,undefined,2));
//				$("#error pre").text("");
				$("#json").JSONView(result, {nl2br:true});
				$("#error").text("");
			}

			var reportError = function(xhr) {
				$("#json").text("Error");
				$("#error").JSONView($.parseJSON(xhr.responseText));
//				$("#assignmentsList pre").text("Error");
//				$("#error pre").text(JSON.stringify($.parseJSON(xhr.responseText),undefined,2));
			}

			$("#listDefinedTasksButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks",
							success : function(result) {
								reportSuccess(result);
								$("#taskId").val(result[0]);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});

			
			$("#listTestingTasksButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks/testing",
							success : function(result) {
								reportSuccess(result);
								$("#taskId").val(result[0].taskId);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});

			$("#listRegisteredTasksButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks/registered",
							success : function(result) {
								reportSuccess(result);
								$("#taskId").val(result[0].taskId);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});

			$("#createTaskButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "POST",
							url : "api/tasks/create",
							success : function(result) {
								reportSuccess(result);
								$("#taskId").val(result[0].taskId);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});
			
			$("#updateTestingTaskButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "POST",
							url : "api/tasks/" + $("#taskId").val()+"/update",
							data : {'sha1' : $("#tasksha1").val() },
							success : function(result) {
								reportSuccess(result);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});


			$("#pollUpdateButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks/" + $("#taskId").val()+"/update_status",
							success : function(result) {
								reportSuccess(result);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});
			
			$("#registerTaskButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "POST",
							url : "api/tasks/" + $("#taskId").val()+"/register",
							data : {'sha1' : $("#tasksha1").val() },
							success : function(result) {
								reportSuccess(result);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});


			$("#pollRegistrationButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks/" + $("#taskId").val()+"/registering_status",
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
					url: 'api/repo',
					type: 'POST',
					data : {"taskId" : $("#taskId").val() },
					success: function (result) {
						reportSuccess(result);
						$("#repoId").val(result.repoId);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;				
			});

			$("#startTestingTaskForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo',
					type: 'POST',
					data : {"taskId" : $("#taskId").val(), "usingTestingVersion":"true" },
					success: function (result) {
						reportSuccess(result);
						$("#repoId").val(result.repoId);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;				
			});
			
			$("#listRepoTags").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo/'+$("#repoId").val(),
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
			
			$("#listRepo").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo/'+$("#repoId").val()+"/"+$("#repoTag").val(),
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
			

			$("#updateRepo").submit(function(event) {
				event.preventDefault();
				var formData = new FormData($(this)[0]);
				$.ajax({
					url: 'api/repo/'+$("#repoId").val()+"/"+$("#repoTag").val()+"/"+$("#fileName").val(),
					type: 'POST',
					data: formData,
					cache: false,
					contentType: false,
					processData: false,
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;

			});

			$("#deleteFromRepo").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo/'+$("#repoId").val()+"/"+$("#repoTag").val()+"/"+$("#deleteFile").val(),
					type: 'DELETE',
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;				
			});

			$("#resetRepo").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo/'+$("#repoId").val()+"/reset/"+$("#resetTag").val(),
					type: 'POST',
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});

				return false;				
			});

			$("#readRepo").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo/'+$("#repoId").val()+"/"+$("#repoTag").val()+"/"+$("#readFile").val(),
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
			


			$("#tagRepo").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo/'+$("#repoId").val(),
					type: 'POST',
					success: function (result) {
						reportSuccess(result);
						$("#submissionTag").val(result.tag);
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
					url: 'api/submissions/'+$("#repoId").val()+'/'+$("#submissionTag").val(),
					type: 'POST',
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
					url: 'api/submissions/'+$("#repoId").val()+'/'+$("#submissionTag").val(),
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