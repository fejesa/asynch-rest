package io.crunch.rest;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.CompletionCallback;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * REST resource that provides activity-related endpoints with asynchronous processing.
 * <p>
 * This class demonstrates both reactive and suspended asynchronous processing using Jakarta RESTful Web Services.
 * It utilizes a managed executor service for handling requests asynchronously.
 * </p>
 * Note: {@code ApplicationScoped} is needed for the {@code ManagedExecutorService} to be injected
 */
@ApplicationScoped
@Path("/activities")
public class ActivityResource {

    private final Logger log = Logger.getLogger(ActivityResource.class);

    /**
     * Injects the default managed executor service provided by the Jakarta EE platform.
     * <p>
     * The default Managed Executor Service instance’s JNDI name is <i>java:comp/DefaultManagedExecutorService</i>.
     * This executor service is used to handle asynchronous request execution.
     * </p>
     */
    @Resource(mappedName = "java:comp/DefaultManagedExecutorService")
    private ManagedExecutorService managedExecutorService;

    private static final String ACTIVITIES =
            """
            ["Running", "Swimming", "Cycling"]
            """;
    private static final Random random = new Random();

    /**
     * Asynchronously retrieves a list of activities using a reactive programming model.
     * <p>
     * This method executes the logic asynchronously using {@link CompletableFuture} and the managed executor service.
     * If the operation is successful, a JSON response with activity types is returned.
     * If an error occurs, it is logged and propagated back as an exception.
     * </p>
     *
     * @return A {@link CompletionStage} that completes with an HTTP response containing the activity list or an error.
     */
    @GET
    @Path("/reactive")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response> getActivities() {
        var response = new CompletableFuture<Response>();
        managedExecutorService.execute(() -> {
            try {
                if (random.nextBoolean()) {
                    throw new CustomException("An error occurred");
                }
                longRunningTask();
                response.complete(Response.ok(ACTIVITIES).build());
                log.info("Reactive - Request completed successfully.");
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.error("Reactive - Request completed with error: " + e.getMessage());
                response.completeExceptionally(e);
            }
        });
        log.info("Reactive - Request is being processed asynchronously.");
        return response;
    }

    /**
     * Retrieves a list of activities using the suspended request model.
     * <p>
     * This method leverages {@link AsyncResponse} to handle the request asynchronously.
     * The response will timeout after 5 seconds if not completed.
     * A completion callback is registered to log request status.
     * </p>
     *
     * @param asyncResponse The suspended asynchronous response object used to send the result.
     */
    @GET
    @Path( "/suspended")
    @Produces(MediaType.APPLICATION_JSON)
    public void getActivities(@Suspended AsyncResponse asyncResponse) {
        // Set timeout behavior: return 503 Service Unavailable if request takes too long
        asyncResponse.setTimeoutHandler(response ->
                response.resume(Response.status(SERVICE_UNAVAILABLE)
                        .entity("Operation timed out").build()));
        asyncResponse.setTimeout(5, TimeUnit.SECONDS);

        // Register a callback to log completion status
        asyncResponse.register((CompletionCallback) error -> {
            if (error != null) {
                log.error("Suspended - Request completed with error: " + error.getMessage());
            } else {
                log.info("Suspended - Request completed successfully.");
            }
        });

        // Execute the asynchronous task using the managed executor service
        managedExecutorService.submit(() -> {
            try {
                if (random.nextBoolean()) {
                    throw new CustomException("An error occurred");
                }
                longRunningTask();
                asyncResponse.resume(ACTIVITIES);
                log.info("Suspended - Request completed successfully.");
            } catch (Exception e) {
                log.error("Suspended - Request completed with error: " + e.getMessage());
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                // By default, the response is set to 500 Internal Server Error
                // If we set the response to a specific status or message, the CompletionCallback is invoked without an error
                asyncResponse.resume(e);
            }
        });
        log.info("Suspended - Request is being processed asynchronously.");
    }

    /**
     * Simulates a long-running task.
     *
     * @throws InterruptedException If the task is interrupted.
     */
    private void longRunningTask() throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(3L + random.nextInt(4)));
    }
}
