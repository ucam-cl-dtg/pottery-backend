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
$(document).ready(
		function() {

			var submissionId = 0;

			var reportSuccess = function(result) {
//				$("#assignmentsList pre").text(JSON.stringify(result,undefined,2));
//				$("#error pre").text("");
				$("#error").text("");
                if (Object.prototype.toString.call(result) === '[object String]') {
                    try {
                        result = JSON.parse(result);
                    } catch {
                        $("#json").html("<pre>").children().text(result);
                        return;
                    }
                }
                $("#json").JSONView(result, {nl2br:true});
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


			$("#listRetiredTasksButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							type : "GET",
							url : "api/tasks/retired",
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

			$("#retireTaskButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							url: 'api/tasks/'+$("#taskId").val()+"/retire",
							type: 'POST',
							success: function (result) {
								reportSuccess(result);
								$("#repoId").val(result.repoId);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});


			$("#unretireTaskButton").click(
					function(e) {
						e.preventDefault();
						$.ajax({
							url: 'api/tasks/'+$("#taskId").val()+"/unretire",
							type: 'POST',
							success: function (result) {
								reportSuccess(result);
								$("#repoId").val(result.repoId);
							},
							error : function(xhr,textStatus,errorThrown) {
								reportError(xhr);
							}
						});
					});

			$("#createRemoteTaskForm").submit(function(event) {
			  event.preventDefault();
			  $.ajax({
                  url: 'api/tasks/create_remote',
                  type: 'POST',
                  data: {"remote" : $("#remoteURI").val() },
                  success : function(result) {
                      reportSuccess(result);
                      $("#taskId").val(result.taskId);
                  },
                  error : function(xhr,textStatus,errorThrown) {
                      reportError(xhr);
                  }
              })
            });

			$("#startTaskForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/repo',
					type: 'POST',
					data : {"taskId" : $("#taskId").val(), "validityMinutes": $("#validity").val(), "variant": $("#variant").val() },
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
					data : {"taskId" : $("#taskId").val(), "usingTestingVersion":"true", "validityMinutes": $("#validityTest").val(), "variant": $("#variantTest").val() },
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


            $("#startRemoteTaskForm").submit(function(event) {
                event.preventDefault();
                $.ajax({
                    url: 'api/repo/remote',
                    type: 'POST',
                    data : {"taskId" : $("#taskId").val(), "validityMinutes": $("#validityRemote").val(),"remote":$("#repoRemote").val(), "variant": $("#variantRemote").val() },
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

			$("#pollOutputForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/submissions/'+$("#repoId").val()+'/'+$("#submissionTag").val()+'/output/'+$("#stepName").val(),
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


			$("#setWorkersForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/worker/resize',
					type: 'POST',
					data : {"numThreads" : $("#workerThreads").val() },
					success: function (result) {
						reportSuccess(result);
					},
					error : function(xhr,textStatus,errorThrown) {
						reportError(xhr);
					}
				});
				return false;
			});

			$("#getWorkQueueForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/worker',
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

			$("#serverStatusForm").submit(function(event) {
				event.preventDefault();
				$.ajax({
					url: 'api/status',
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