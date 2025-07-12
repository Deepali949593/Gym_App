package org.example.gym;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.example.gym.routes.handlers;
import org.example.gym.services.database;

public class MainVerticle extends AbstractVerticle {

    private MongoClient mongoClient;

    @Override
    public void start(Promise<Void> startPromise) {
        // Create MongoDB client
        mongoClient = database.getClient(vertx);

        // Create router
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // ✅ USER routes
        router.post("/api/users").handler(ctx -> handlers.registerUser(ctx, mongoClient));                // Register
        router.post("/api/login").handler(ctx -> handlers.loginUser(ctx, mongoClient));                   // Login
        router.put("/api/change-password/:id").handler(ctx -> handlers.changePassword(ctx, mongoClient)); // Change password
        router.put("/api/forgot-password").handler(ctx -> handlers.forgotPassword(ctx, mongoClient));     // Forgot password
        router.post("/api/reset-password").handler(ctx -> handlers.resetPassword(ctx, mongoClient));      // Reset password
        router.get("/api/profile/:id").handler(ctx -> handlers.viewProfile(ctx, mongoClient));            // View profile

        // ✅ EVENT routes
        router.post("/api/events").handler(ctx -> handlers.createEvent(ctx, mongoClient));                // Create event
        router.get("/api/events").handler(ctx -> handlers.getAllEvents(ctx, mongoClient));                // Get all events
        router.delete("/api/events/:id").handler(ctx -> handlers.deleteEvent(ctx, mongoClient));          // Delete event
        router.post("/api/events/:eventId/book").handler(ctx -> handlers.bookEventSlot(ctx, mongoClient));// Book event slot

        // ✅ FEEDBACK routes
        router.post("/api/feedback").handler(ctx -> handlers.addFeedback(ctx, mongoClient));              // Add feedback
        router.get("/api/feedback").handler(ctx -> handlers.getFeedbacks(ctx, mongoClient));              // Get feedback

        // ✅ CONTACT routes
        router.post("/api/contact").handler(ctx -> handlers.contactUs(ctx, mongoClient));                 // Submit contact
        router.get("/api/contact").handler(ctx -> handlers.getContacts(ctx, mongoClient));                // Get contact

        // ✅ PAYMENT routes
        router.post("/api/create-payment-session").handler(ctx -> handlers.createStripeCheckoutSession(ctx, mongoClient));
        router.post("/api/stripe-checkout").handler(ctx -> handlers.createStripeCheckoutSession(ctx, mongoClient)); // ✅ Fixed


        // ✅ Start the HTTP Server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8888, http -> {
                    if (http.succeeded()) {
                        System.out.println("✅ HTTP server started on port 8888");
                        startPromise.complete();
                    } else {
                        System.out.println("❌ Failed to launch server: " + http.cause());
                        startPromise.fail(http.cause());
                    }
                });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle());
    }
}
