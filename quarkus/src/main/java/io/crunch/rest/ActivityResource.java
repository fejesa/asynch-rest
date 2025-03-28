package io.crunch.rest;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.CompletionCallback;
import jakarta.ws.rs.container.ConnectionCallback;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

@Path("/activity")
public class ActivityResource {

    private static final String ACTIVITIES =
            """
            ["Running", "Swimming", "Cycling"]
            """;

    private static final Random random = new Random();

    static {
        Infrastructure.setDroppedExceptionHandler(err ->
                Log.error("XXX Mutiny dropped exception")
        );
    }

    private final ScheduledExecutorService executor = Infrastructure.getDefaultWorkerPool();

    @GET
    @Path( "/suspended")
    @Produces(MediaType.APPLICATION_JSON)
    public void getActivities(@Suspended AsyncResponse asyncResponse) {
        // Set timeout behavior: return 503 Service Unavailable if request takes too long
        asyncResponse.setTimeoutHandler(response -> {
            Log.warn("Suspended - Request timed out");
            response.resume(Response.status(SERVICE_UNAVAILABLE).entity("Suspended - Operation timed out").build());
        });
        asyncResponse.setTimeout(8, TimeUnit.SECONDS);

        // Register a callback to log completion status
        asyncResponse.register((CompletionCallback) error -> {
            if (error != null) {
                Log.error("Suspended - Request completed with error: " + error.getMessage());
            } else {
                Log.info("Suspended - Request completed");
            }
        });

        // Register a callback to cancel the task if the client disconnects
        // Note: When the client disconnects this callback is never called
        asyncResponse.register(new ConnectionCallback() {
            @Override
            public void onDisconnect(AsyncResponse disconnected) {
                Log.warn("Client disconnected. Cancelling task.");
                disconnected.cancel();
            }
        });

        // Execute the asynchronous task using the managed executor service
        executor.submit(() -> {
            try {
                if (random.nextBoolean()) {
                    throw new CustomException("An error occurred");
                }
                longRunningTask();
                if (asyncResponse.isSuspended()) {
                    asyncResponse.resume(ACTIVITIES);
                    Log.info("Suspended - Response sent successfully");
                } else {
                    Log.warn("Suspended - Response not sent, ignored"); // Timeout occurred
                }
            } catch (Exception e) {
                Log.error("Suspended - Error during task execution");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                // By default, the response is set to 500 Internal Server Error
                // If we set the response to a specific status or message, the CompletionCallback is invoked without an error
                asyncResponse.resume(e);
            }
        });

        Log.info("Suspended - Request is being processed asynchronously");
    }

    @GET
    @Path("/reactive")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<RestResponse<String>> getActivities() {
        Log.info("Reactive - Request received");
        return Uni.createFrom()
            .item(Unchecked.supplier(() -> {
                try {
                    if (random.nextBoolean()) {
                        throw new CustomException("An error occurred");
                    }
                    longRunningTask();
                    Log.info("Reactive - Request completed"); // If timeout occurs, or cancelled, the response should not be sent
                    return RestResponse.ok(ACTIVITIES);
                } catch (Exception e) {
                    Log.error("Reactive - Error during task execution");
                    if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    throw e;
                }
            }))
            .ifNoItem().after(Duration.ofSeconds(8)).failWith(() -> {
                Log.warn("Reactive - Request timed out");
                return new ServiceUnavailableException();
            })
            .onFailure().recoverWithItem(() -> {
                Log.error("Reactive - Request completed with error");
                return RestResponse.serverError();
            })
            .onCancellation().invoke(() -> Log.warn("Reactive - Request was cancelled"))
            .runSubscriptionOn(executor);
    }

    /**
     * Simulates a long-running task.
     *
     * @throws InterruptedException If the task is interrupted.
     */
    private void longRunningTask() throws InterruptedException {
        var duration = 5 + random.nextInt(7);
        Log.info("Long-running task started. Duration: " + duration + " seconds");
        for (int i = 0; i < duration; i++) {
            Thread.sleep(1000);
            if (Thread.currentThread().isInterrupted()) {
                Log.error("Long-running task interrupted");
                throw new InterruptedException("Task interrupted");
            }
        }
    }
}
