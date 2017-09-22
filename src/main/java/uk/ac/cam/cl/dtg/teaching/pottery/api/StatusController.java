package uk.ac.cam.cl.dtg.teaching.pottery.api;

import com.wordnik.swagger.annotations.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.Map;

public interface StatusController {
    @GET
    @Path("/")
    @ApiOperation(
      value = "Get server status",
      response = String.class,
      responseContainer = "Map",
      position = 0
    )
    Map<String, String> getStatus();
}
