package org.example.gym.services;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

public class database {

    private static MongoClient client;

    public static MongoClient getClient(Vertx vertx) {
        if (client == null) {
            JsonObject config = new JsonObject()
                    .put("connection_string", "mongodb+srv://24mcab44:paul2329@cluster0.0wnoefe.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0")
                    .put("db_name", "gymDB");
            client = MongoClient.createShared(vertx, config);
        }
        return client;
    }
}
