package org.example.gym;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.time.Instant;

public class MainVerticle extends AbstractVerticle {

    private MongoClient mongoClient;

    @Override
    public void start() {

        // Initialize MongoDB client
        mongoClient = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "gymDB"));

        // Create a router object
        Router router = Router.router(vertx);

        // Enable body parsing
        router.route().handler(BodyHandler.create());

        router.post("/api/users").handler(ctx -> {
            JsonObject body = ctx.getBodyAsJson();

            // Validate required fields
            if (body == null || !body.containsKey("name") || !body.containsKey("email") ||
                    !body.containsKey("phone") || !body.containsKey("gender") ||
                    !body.containsKey("age") || !body.containsKey("password")) {

                ctx.response().setStatusCode(400).end("Missing required fields.");
                return;
            }

            // Add timestamps
            String now = Instant.now().toString();
            body.put("createdAt", now);
            body.put("updatedAt", now);

            // Insert into MongoDB
            mongoClient.insert("users", body, res -> {
                if (res.succeeded()) {
                    ctx.response().setStatusCode(201).end("User Registered Successfully");
                } else {
                    ctx.response().setStatusCode(500).end("User Registration Failed");
                }
            });
        });

        router.post("/api/login").handler(ctx -> {
            JsonObject body = ctx.getBodyAsJson();
            String email = body.getString("email");
            String password = body.getString("password");

            if (email == null || password == null) {
                ctx.response().setStatusCode(400).end("Email and Password required");
                return;
            }

            JsonObject query = new JsonObject().put("email", email).put("password", password);

            mongoClient.findOne("users", query, null, res -> {
                if (res.succeeded() && res.result() != null) {
                    ctx.response().setStatusCode(200).end("Login Successful");
                } else {
                    ctx.response().setStatusCode(401).end("Invalid Credentials");
                }
            });
        });

        router.put("/api/change-password/:id").handler(ctx -> {
            String userId = ctx.pathParam("id");
            JsonObject body = ctx.getBodyAsJson();

            String oldPassword = body.getString("oldPassword");
            String newPassword = body.getString("newPassword");

            if (oldPassword == null || newPassword == null) {
                ctx.response().setStatusCode(400).end("Both old and new passwords are required.");
                return;
            }

            JsonObject query = new JsonObject().put("_id", userId).put("password", oldPassword);
            JsonObject update = new JsonObject()
                    .put("$set", new JsonObject()
                            .put("password", newPassword)
                            .put("updatedAt", Instant.now().toString()));

            mongoClient.findOneAndUpdate("users", query, update, res -> {
                if (res.succeeded() && res.result() != null) {
                    ctx.response().setStatusCode(200).end("Password Updated Successfully");
                } else {
                    ctx.response().setStatusCode(400).end("Invalid User ID or Incorrect Old Password");
                }
            });
        });





        // Start the HTTP server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888, http -> {
                    if (http.succeeded()) {
                        System.out.println("HTTP server started on port 8888");
                    } else {
                        System.out.println("Failed to launch server: " + http.cause());
                    }
                });
    }

    // Main method to run the verticle
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle());
    }
}
