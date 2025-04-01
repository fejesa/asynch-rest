# Asynchronous REST API based on Jakarta RESTful Web Services and Mutiny

This project provides a RESTful API example endpoints with asynchronous processing using Jakarta RESTful Web Services and Mutiny.

## Overview
This API demonstrates both reactive and suspended asynchronous processing. It handles long-running tasks while managing client connections and errors gracefully.

### Features

- **Asynchronous HTTP**: Efficient use of threads by suspending requests until a response is ready.
- **Reactive Processing**: Uses Mutiny and SmallRye to handle long-running tasks reactively.
- **Suspended Processing**: Uses `AsyncResponse` to suspend and resume requests upon task completion or timeout.
- **Asynchronous Error Handling**: Handles timeouts, disconnections, and task failures gracefully.
- **Lifecycle Callbacks**: Monitors request progress and handles cancellation or errors.
- **Executor Service**: Uses `ScheduledExecutorService` for executing asynchronous tasks.

## Endpoints

### GET /activity/suspended

Suspended processing endpoint that handles long-running tasks asynchronously.

- **Timeout**: Returns 503 Service Unavailable if the request takes too long.
- **Disconnection**: Cancels the task if the client disconnects.
- **Completion**: Logs the completion status and sends the response.

### GET /activity/reactive

Reactive processing endpoint that handles long-running tasks using Mutiny.

- **Timeout**: Fails with a `ServiceUnavailableException` if the request takes too long.
- **Failure**: Recovers with a server error response.
- **Cancellation**: Logs if the request is cancelled.

## Technologies Used

- **Java 21**
- **Jakarta RESTful Web Services**
- **Mutiny**
- **SmallRye**
- **Maven**

## How to Run

1. Clone the repository.
2. Navigate to the project directory.
3. Build and deploy the application in WildFly:
    ```sh
    mvn -f wildfly/pom.xml clean package wildfly:run
    ```
4. Access the API at `http localhost:8080/wilfly-rest/activity/suspended` or `http localhost:8080/wilfly-rest/activity/reactive` or you can use your browser to access the endpoints.
5. Build and deploy the application in Quarkus:
    ```sh
    mvn -f quarkus/pom.xml clean quarkus:dev
    ```
6. Access the API at `http localhost:8080/activity/suspended` or `http localhost:8080/activity/reactive` or you can use your browser to access the endpoints.

Note: I use [httpie](https://httpie.io/) to test the endpoints. You can use any other tool like Postman or curl.
