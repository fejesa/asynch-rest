package io.crunch.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class CustomExceptionMapper implements ExceptionMapper<CustomException> {

    private final Logger log = Logger.getLogger(CustomExceptionMapper.class);

    @Override
    public Response toResponse(CustomException exception) {
        log.error("Custom exception has occurred in the service: " + exception.getMessage());
        return Response.serverError().build();
    }
}
