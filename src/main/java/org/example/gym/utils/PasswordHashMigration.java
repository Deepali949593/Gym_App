package org.example.gym.utils;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.List;

public class PasswordHashMigration {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject()
                .put("connection_string", "mongodb://localhost:27017")
                .put("db_name", "gymDB"));

        // Find users with a default password like 'RESETME'
        JsonObject query = new JsonObject().put("password", "RESETME");

        mongoClient.find("users", query, result -> {
            if (result.succeeded()) {
                List<JsonObject> users = result.result();

                for (JsonObject user : users) {
                    String name = user.getString("name");
                    String email = user.getString("email");
                    String id = user.getString("_id");

                    if (email == null) {
                        System.out.println("⏭ Skipping " + name + " (no email)");
                        continue;
                    }

                    // Generate a new random password
                    String newPassword = generateRandomPassword(10);
                    String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt());

                    JsonObject filter = new JsonObject().put("_id", id);
                    JsonObject update = new JsonObject()
                            .put("$set", new JsonObject().put("password", hashed));

                    mongoClient.updateCollection("users", filter, update, updateRes -> {
                        if (updateRes.succeeded()) {
                            System.out.println("✅ Password hashed for: " + name);

                            String subject = "🔐 Your New Gym Login Password";
                            String body = String.format(
                                    "Hi %s,\n\nYour new password for the Gym App is:\n\nPassword: %s\n\nPlease log in and change it immediately.\n\nRegards,\nGym Team",
                                    name, newPassword
                            );

                            EmailUtil.sendEmail(email, subject, body);

                        } else {
                            System.err.println("❌ MongoDB update failed for " + name + ": " + updateRes.cause());
                        }
                    });
                }

            } else {
                System.err.println("❌ Failed to fetch users: " + result.cause());
            }
        });
    }
}
