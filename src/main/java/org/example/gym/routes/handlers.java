package org.example.gym.routes;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.RoutingContext;
import org.example.gym.utils.PasswordUtil;
import org.example.gym.utils.EmailUtil;
import at.favre.lib.crypto.bcrypt.BCrypt;


import java.time.Instant;

public class handlers {

    public static void registerUser(RoutingContext ctx, MongoClient client) {
        JsonObject body = ctx.getBodyAsJson();

        String name = body.getString("name");
        String email = body.getString("email");
        String phone = body.getString("phone");
        Double height = body.getDouble("height");
        Double weight = body.getDouble("weight");
        Integer age = body.getInteger("age");
        String gender = body.getString("gender");
        String fitnessGoal = body.getString("fitnessGoal");

        // Basic validations
        if (name == null || email == null || phone == null || height == null || weight == null || age == null || gender == null) {
            ctx.response().setStatusCode(400).end("All fields (name, email, phone, height, weight, age, gender, fitnessGoal) are required.");
            return;
        }

        // Check if user already exists
        client.findOne("users", new JsonObject().put("email", email), null, res -> {
            if (res.succeeded()) {
                if (res.result() != null) {
                    ctx.response().setStatusCode(409).end("User already registered.");
                } else {
                    // New user registration
                    String plainPassword = PasswordUtil.generateRandomPassword(10);

                    if (!PasswordUtil.isStrongPassword(plainPassword)) {
                        ctx.response().setStatusCode(400).end("Generated password is weak. Try again.");
                        return;
                    }

                    String hashedPassword = PasswordUtil.hashPassword(plainPassword);

                    JsonObject newUser = new JsonObject()
                            .put("name", name)
                            .put("email", email)
                            .put("phone", phone)
                            .put("height", height)
                            .put("weight", weight)
                            .put("age", age)
                            .put("gender", gender)
                            .put("fitnessGoal", fitnessGoal)
                            .put("password", hashedPassword)
                            .put("createdAt", Instant.now().toString())
                            .put("updatedAt", Instant.now().toString());

                    client.insert("users", newUser, insertRes -> {
                        if (insertRes.succeeded()) {
                            // Send welcome email
                            String subject = "🎉 Welcome to Gym App - Login Credentials";
                            String bodyText = String.format("Hello %s,\n\nYour account has been created.\nEmail: %s\nPassword: %s\n\nPlease change your password after logging in.", name, email, plainPassword);
                            EmailUtil.sendEmail(email, subject, bodyText);
                            ctx.response().setStatusCode(201).end("✅ User registered and password sent via email.");
                        } else {
                            ctx.response().setStatusCode(500).end("❌ Failed to register user.");
                        }
                    });
                }
            } else {
                ctx.response().setStatusCode(500).end("❌ Error checking user existence.");
            }
        });
    }


    // Login
    public static void loginUser(RoutingContext ctx, MongoClient client) {
        JsonObject body = ctx.getBodyAsJson();
        String email = body.getString("email");
        String password = body.getString("password");

        if (email == null || password == null) {
            ctx.response().setStatusCode(400).end("Email and Password required");
            return;
        }

        client.findOne("users", new JsonObject().put("email", email), null, res -> {
            if (res.succeeded() && res.result() != null) {
                String storedHash = res.result().getString("password");
                if (PasswordUtil.verifyPassword(password, storedHash)) {
                    ctx.response().setStatusCode(200).end("Login Successful");
                } else {
                    ctx.response().setStatusCode(401).end("Invalid Credentials");
                }
            } else {
                ctx.response().setStatusCode(401).end("Invalid Credentials");
            }
        });
    }

    // Change Password
    public static void changePassword(RoutingContext ctx, MongoClient client) {
        String userId = ctx.pathParam("id");
        JsonObject body = ctx.getBodyAsJson();

        if (userId == null || body == null ||
                !body.containsKey("oldPassword") || !body.containsKey("newPassword")) {
            ctx.response().setStatusCode(400).end("Missing userId, oldPassword, or newPassword.");
            return;
        }

        String oldPassword = body.getString("oldPassword");
        String newPassword = body.getString("newPassword");

        // Check new password strength
        if (!PasswordUtil.isStrongPassword(newPassword)) {
            ctx.response().setStatusCode(400).end("New password is too weak. Please use a stronger one.");
            return;
        }

        JsonObject query = new JsonObject().put("_id", userId);

        client.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                String currentHash = res.result().getString("password");

                if (PasswordUtil.verifyPassword(oldPassword, currentHash)) {
                    String newHashedPassword = PasswordUtil.hashPassword(newPassword);

                    JsonObject update = new JsonObject().put("$set", new JsonObject()
                            .put("password", newHashedPassword)
                            .put("updatedAt", Instant.now().toString()));

                    client.updateCollection("users", query, update, updateRes -> {
                        if (updateRes.succeeded()) {
                            ctx.response().setStatusCode(200).end("✅ Password updated successfully.");
                        } else {
                            ctx.response().setStatusCode(500).end("❌ Failed to update password.");
                        }
                    });
                } else {
                    ctx.response().setStatusCode(401).end("❌ Incorrect old password.");
                }
            } else {
                ctx.response().setStatusCode(404).end("❌ User not found.");
            }
        });
    }

    public static void resetPassword(RoutingContext ctx, MongoClient mongoClient) {
        JsonObject body = ctx.body().asJsonObject();
        String email = body.getString("email");
        String token = body.getString("token");
        String newPassword = body.getString("newPassword");

        // Check new password strength
        if (!PasswordUtil.isStrongPassword(newPassword)) {
            ctx.response().setStatusCode(400).end("Password is too weak. Please use a strong password.");
            return;
        }

        mongoClient.findOne("users", new JsonObject().put("email", email).put("resetToken", token), null, res -> {
            if (res.succeeded() && res.result() != null) {
                String expiryStr = res.result().getString("tokenExpiry");
                long expiryTime = Instant.parse(expiryStr).toEpochMilli();

                if (System.currentTimeMillis() > expiryTime) {
                    ctx.response().setStatusCode(400).end("❌ Token expired.");
                    return;
                }

                String hashed = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());

                JsonObject update = new JsonObject().put("$set", new JsonObject()
                        .put("password", hashed)
                        .put("resetToken", null)
                        .put("tokenExpiry", null)
                        .put("updatedAt", Instant.now().toString()));

                mongoClient.updateCollection("users", new JsonObject().put("email", email), update, updateRes -> {
                    if (updateRes.succeeded()) {
                        ctx.response().end("✅ Password reset successful.");
                    } else {
                        ctx.response().setStatusCode(500).end("❌ Failed to reset password.");
                    }
                });
            } else {
                ctx.response().setStatusCode(404).end("❌ Invalid token or email.");
            }
        });
    }

    //forgot password
    public static void forgotPassword(RoutingContext ctx, MongoClient client) {
        String email = ctx.getBodyAsJson().getString("email");

        if (email == null) {
            ctx.response().setStatusCode(400).end("Email required");
            return;
        }

        String token = PasswordUtil.generateResetToken();
        JsonObject query = new JsonObject().put("email", email);
        JsonObject update = new JsonObject().put("$set", new JsonObject()
                .put("resetToken", token)
                .put("tokenExpiry", Instant.now().plusSeconds(120).toString())); // 2 mins expiry

        client.findOneAndUpdate("users", query, update, res -> {
            if (res.succeeded() && res.result() != null) {
                String subject = "🔐 Gym App Password Reset";
                String body = "Use the following token to reset your password:\n\n" + token + "\n\nThis will expire in 30 minutes.";
                EmailUtil.sendEmail(email, subject, body);
                ctx.response().setStatusCode(200).end("Reset token sent to email");
            } else {
                ctx.response().setStatusCode(404).end("Email not found");
            }
        });
    }

    public static boolean isStrongPassword(String password) {
        return password != null &&
                password.length() >= 8 &&
                password.matches(".*[A-Z].*") &&       // at least one uppercase
                password.matches(".*[a-z].*") &&       // at least one lowercase
                password.matches(".*\\d.*") &&         // at least one number
                password.matches(".*[!@#$%^&*()_+=<>?{}\\[\\]~`|\\\\].*"); // at least one special character
    }


    // View Profile
    public static void viewProfile(RoutingContext ctx, MongoClient client) {
        String id = ctx.pathParam("id");
        JsonObject query = new JsonObject().put("_id", id);

        client.findOne("users", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(res.result().encodePrettily());
            } else {
                ctx.response().setStatusCode(404).end("User not found");
            }
        });
    }

    // Create Event
    public static void createEvent(RoutingContext ctx, MongoClient client) {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null || !body.containsKey("name") || !body.containsKey("title")
                || !body.containsKey("date") || !body.containsKey("numOfParticipants")
                || !body.containsKey("modeOfPayment")) {
            ctx.response().setStatusCode(400).end("Missing required fields.");
            return;
        }

        body.put("createdAt", Instant.now().toString());

        client.insert("events", body, res -> {
            if (res.succeeded()) {
                ctx.response().setStatusCode(201).end("Event Created Successfully");
            } else {
                ctx.response().setStatusCode(500).end("Failed to Create Event");
            }
        });
    }

    public static void getAllEvents(RoutingContext ctx, MongoClient client) {
        client.find("events", new JsonObject(), res -> {
            if (res.succeeded()) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(res.result().toString());
            } else {
                ctx.response().setStatusCode(500).end("Failed to fetch events");
            }
        });
    }

    public static void deleteEvent(RoutingContext ctx, MongoClient client) {
        String id = ctx.pathParam("id");
        JsonObject query = new JsonObject().put("_id", id);

        client.removeDocument("events", query, res -> {
            if (res.succeeded()) {
                ctx.response().setStatusCode(200).end("Event deleted successfully");
            } else {
                ctx.response().setStatusCode(500).end("Failed to delete event");
            }
        });
    }

    // ✅ Stripe Checkout Session Handler
    public static void createStripeCheckoutSession(RoutingContext ctx, MongoClient mongoClient) {
        Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY");

        JsonObject requestBody = ctx.getBodyAsJson();
        String userEmail = requestBody.getString("email"); // Email must be passed in body
        if (userEmail == null) {
            ctx.response().setStatusCode(400).end("❌ Email is required for payment.");
            return;
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://www.google.com") // You can change this
                .setCancelUrl("https://www.google.com")
                .setCustomerEmail(userEmail)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD) // default
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("inr")
                                                .setUnitAmount(470000L) // ₹4700 in paise
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Premium Annual Plan")
                                                                .build()
                                                ).build()
                                ).build()
                ).build();

        Vertx vertx = Vertx.vertx();
        vertx.executeBlocking(promise -> {
            try {
                Session session = Session.create(params);
                promise.complete(session);
            } catch (Exception e) {
                promise.fail(e);
            }
        }, res -> {
            if (res.succeeded()) {
                Session session = (Session) res.result();

                // ✅ MongoDB insert
                // Or pass from MainVerticle
                JsonObject paymentRecord = new JsonObject()
                        .put("userEmail", userEmail)
                        .put("amount", 4700)
                        .put("currency", "INR")
                        .put("status", "pending") // default; update via webhook if needed
                        .put("paymentMethod", "card") // Currently card only; expand later
                        .put("sessionId", session.getId())
                        .put("createdAt", Instant.now().toString());

                mongoClient.insert("payments", paymentRecord, insertRes -> {
                    if (insertRes.succeeded()) {
                        System.out.println("✅ Payment record stored in MongoDB.");
                    } else {
                        System.out.println("❌ Failed to store payment: " + insertRes.cause().getMessage());
                    }
                });

                // Return Stripe URL to frontend
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(new JsonObject().put("checkoutUrl", session.getUrl()).encode());
            } else {
                ctx.response().setStatusCode(500).end("❌ Stripe Error: " + res.cause().getMessage());
            }
        });
    }


    // ✅ Feedback
    public static void addFeedback(RoutingContext ctx, MongoClient client) {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null || !body.containsKey("rating") || !body.containsKey("comment")) {
            ctx.response().setStatusCode(400).end("Rating and comment are required");
            return;
        }
        body.put("submittedAt", Instant.now().toString());
        client.insert("feedback", body, res -> {
            if (res.succeeded()) {
                ctx.response().setStatusCode(201).end("Feedback submitted successfully");
            } else {
                ctx.response().setStatusCode(500).end("Failed to submit feedback");
            }
        });
    }

    public static void getFeedbacks(RoutingContext ctx, MongoClient client) {
        client.find("feedback", new JsonObject(), res -> {
            if (res.succeeded()) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(res.result().toString());
            } else {
                ctx.response().setStatusCode(500).end("Failed to fetch feedback");
            }
        });
    }

    // ✅ Contact
    public static void contactUs(RoutingContext ctx, MongoClient client) {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null || !body.containsKey("name") || !body.containsKey("email") || !body.containsKey("message")) {
            ctx.response().setStatusCode(400).end("Name, email, and message are required");
            return;
        }
        body.put("submittedAt", Instant.now().toString());
        client.insert("contact", body, res -> {
            if (res.succeeded()) {
                ctx.response().setStatusCode(201).end("Message submitted successfully");
            } else {
                ctx.response().setStatusCode(500).end("Failed to submit message");
            }
        });
    }

    public static void getContacts(RoutingContext ctx, MongoClient client) {
        client.find("contact", new JsonObject(), res -> {
            if (res.succeeded()) {
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(res.result().toString());
            } else {
                ctx.response().setStatusCode(500).end("Failed to fetch messages");
            }
        });
    }

    public static void bookEventSlot(RoutingContext ctx, MongoClient client) {
        String eventId = ctx.pathParam("eventId");
        JsonObject user = ctx.getBodyAsJson();

        if (eventId == null || user == null || !user.containsKey("name") || !user.containsKey("email")) {
            ctx.response().setStatusCode(400).end("Missing eventId, name or email.");
            return;
        }

        JsonObject query = new JsonObject().put("_id", eventId);

        client.findOne("events", query, null, res -> {
            if (res.succeeded() && res.result() != null) {
                JsonObject event = res.result();
                int currentSlots = event.getInteger("numOfParticipants");

                if (currentSlots <= 0) {
                    ctx.response().setStatusCode(400).end("❌ No slots available.");
                    return;
                }

                // Decrease numOfParticipants by 1
                JsonObject update = new JsonObject().put("$inc", new JsonObject().put("numOfParticipants", -1));

                client.updateCollection("events", query, update, updateRes -> {
                    if (updateRes.succeeded()) {
                        JsonObject registration = new JsonObject()
                                .put("eventId", eventId)
                                .put("eventTitle", event.getString("title"))
                                .put("userName", user.getString("name"))
                                .put("userEmail", user.getString("email"))
                                .put("registeredAt", Instant.now().toString());

                        client.insert("event_registrations", registration, insertRes -> {
                            if (insertRes.succeeded()) {
                                ctx.response().setStatusCode(200).end("✅ Slot booked successfully.");
                            } else {
                                ctx.response().setStatusCode(500).end("❌ Slot booked, but failed to save registration.");
                            }
                        });
                    } else {
                        ctx.response().setStatusCode(500).end("❌ Failed to update slots.");
                    }
                });

            } else {
                ctx.response().setStatusCode(404).end("❌ Event not found.");
            }
        });
    }

} // 👈 This is the final closing brace of the `handlers` class
