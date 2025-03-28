package io.crunch.rest;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.CompletionCallback;
import jakarta.ws.rs.container.ConnectionCallback;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * REST resource that provides activity-related endpoints with asynchronous processing.
 * <p>
 * The primary use case for asynchronous HTTP is when the client is polling the server for a delayed response.
 * In synchronous HTTP, where the server blocks on incoming and outgoing I/O, a thread is consumed per client connection.
 * This increases memory usage and consumes valuable thread resources. Asynchronous processing is a technique that enables
 * better and more efficient utilization of processing threads.
 * </p>
 *
 * <p>
 * This class demonstrates both reactive and suspended asynchronous processing using Jakarta RESTful Web Services.
 * </p>
 *
 * <h2>Reactive Asynchronous Processing</h2>
 * <p>
 * Whenever a resource method returns a {@link CompletionStage} (where {@code CompletableFuture} is the concrete implementation),
 * the request will be suspended and only resumed when the {@code CompletionStage} is resolved. The resolution can happen in two cases:
 * </p>
 * <ul>
 *     <li>{@code CompletableFuture<T>#complete(T t)} – Completes normally and returns a value.</li>
 *     <li>{@code CompletableFuture<T>#completeExceptionally(Throwable t)} – Completes exceptionally and propagates the error.</li>
 * </ul>
 *
 * <h2>Suspended Asynchronous Processing</h2>
 * <p>
 * The Jakarta RESTful Web Services specification includes built-in support for asynchronous HTTP processing via:
 * </p>
 * <ul>
 *     <li>The {@code @Suspended} annotation</li>
 *     <li>The {@link AsyncResponse} interface</li>
 * </ul>
 * <p>
 * The {@code AsyncResponse} acts as the callback object. Calling one of the {@code resume()} methods sends a response
 * back to the client and terminates the HTTP request. The possible cases are:
 * </p>
 * <ul>
 *     <li>{@code AsyncResponse#resume(T t)} – Completes normally with a result.</li>
 *     <li>{@code AsyncResponse#resume(Throwable t)} – Completes with an error.</li>
 * </ul>
 *
 * <h2>Asynchronous Processing Lifecycle Callbacks</h2>
 * <p>
 * Additionally, we can register lifecycle callback classes to receive events during the asynchronous processing lifecycle.
 * These lifecycle callbacks are optional, and the availability of certain callbacks depends on the JAX-RS runtime implementation.
 * </p>
 *
 * <h2>Timeout Handling</h2>
 * <p>
 * {@code AsyncResponse} also supports timeout settings, where a timeout value can be specified when suspending a connection.
 * This prevents the server from waiting indefinitely for a response.
 * </p>
 *
 * <h2>Demonstration of Asynchronous Processing</h2>
 * <p>
 * This REST API demonstrates both reactive and suspended asynchronous solutions. It simulates long-running processes
 * and can intentionally generate errors to showcase failure handling.
 * </p>
 *
 * <h2>Executor Service</h2>
 * <p>
 * {@code ActivityResource} utilizes a managed executor service to handle requests asynchronously.
 * </p>
 * <p><strong>Note:</strong> {@code @ApplicationScoped} is required to enable injection of the {@code ManagedExecutorService}.</p>
 */
@ApplicationScoped
@Path("/activity")
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
                if (!response.isDone()) {
                    response.complete(Response.ok(ACTIVITIES).build());
                    log.info("Reactive - Request completed successfully");
                } else {
                    log.warn("Reactive - Response not sent, ignored"); // Timeout occurred
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.error("Reactive - Error during task execution");
                response.completeExceptionally(e);
            }
        });
        response.completeOnTimeout(Response.status(SERVICE_UNAVAILABLE).entity("Reactive - Operation timed out").build(), 8, TimeUnit.SECONDS);

        log.info("Reactive - Request is being processed asynchronously");
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
        asyncResponse.setTimeoutHandler(response -> {
            log.warn("Suspended - Request timed out");
            response.resume(Response.status(SERVICE_UNAVAILABLE).entity("Suspended - Operation timed out").build());
        });
        asyncResponse.setTimeout(8, TimeUnit.SECONDS);

        // Register a callback to log completion status
        asyncResponse.register((CompletionCallback) error -> {
            if (error != null) {
                log.error("Suspended - Request completed with error: " + error.getMessage());
            } else {
                log.info("Suspended - Request completed");
            }
        });

        // Register a callback to cancel the task if the client disconnects
        // Note: When the client disconnects this callback is never called
        asyncResponse.register((ConnectionCallback) disconnected -> {
            log.warn("Client disconnected. Cancelling task.");
            disconnected.cancel();
        });

        // Execute the asynchronous task using the managed executor service
        managedExecutorService.submit(() -> {
            try {
                if (random.nextBoolean()) {
                    throw new CustomException("An error occurred");
                }
                longRunningTask();
                if (asyncResponse.isSuspended()) {
                    asyncResponse.resume(ACTIVITIES);
                    log.info("Suspended - Response sent successfully");
                } else {
                    log.warn("Suspended - Response not sent, ignored"); // Timeout occurred
                }
            } catch (Exception e) {
                log.error("Suspended - Error during task execution");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                // By default, the response is set to 500 Internal Server Error
                // If we set the response to a specific status or message, the CompletionCallback is invoked without an error
                asyncResponse.resume(e);
            }
        });

        log.info("Suspended - Request is being processed asynchronously");
    }

    /**
     * Simulates a long-running task.
     *
     * @throws InterruptedException If the task is interrupted.
     */
    private void longRunningTask() throws InterruptedException {
        var duration = 5 + random.nextInt(7);
        log.info("Long-running task started. Duration: " + duration + " seconds");
        for (int i = 0; i < duration; i++) {
            Thread.sleep(1000);
            if (Thread.currentThread().isInterrupted()) {
                log.error("Long-running task interrupted");
                throw new InterruptedException("Task interrupted");
            }
        }
    }
}
