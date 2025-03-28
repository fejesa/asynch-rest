package io.crunch.rest;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.Duration;
import java.util.Random;

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
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
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
