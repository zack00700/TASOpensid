// src/main/java/fr/alb/services/ItemDiffService.java
package fr.alb.yard.service;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import fr.alb.dto.items.ItemDiffPayload;
import fr.alb.yard.model.Item;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Filters.*;

@ApplicationScoped
public class ItemDiffService {

    @Inject
    MongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    private MongoCollection<Document> documentCollection() {
        // Reuse the exact same collection name as Panache uses for Item
        String collName = Item.mongoCollection().getNamespace().getCollectionName();
        return mongoClient.getDatabase(databaseName).getCollection(collName, Document.class);
    }

    public static class ApplyDiffResult {
        public int inserted;
        public int modified;
        public int deleted;
    }

    public ApplyDiffResult applyItemDiff(String blId, ItemDiffPayload payload) {
        MongoCollection<Document> coll = documentCollection();

        List<WriteModel<Document>> ops = new ArrayList<>();

        // INSERTS
        if (payload.newItems != null) {
            for (Map<String, Object> map : payload.newItems) {
                Document doc = new Document(map);
                doc.remove("id");
                doc.put("billOfLadingId", blId);
                // If your _id is ObjectId, let Mongo assign it; if String, ensure it's not present
                ops.add(new InsertOneModel<>(doc));
            }
        }

        // UPDATES
        if (payload.updatedItems != null) {
            for (Map<String, Object> patchMap : payload.updatedItems) {
                Object idObj = patchMap.get("id");
                if (idObj == null) continue;
                String id = idObj.toString();

                Document setDoc = new Document();
                for (var e : patchMap.entrySet()) {
                    if ("id".equals(e.getKey())) continue;
                    setDoc.put(e.getKey(), e.getValue());
                }
                if (setDoc.isEmpty()) continue;

                ops.add(new UpdateOneModel<>(
                        and(eq("_id", toAnyId(id)), eq("billOfLadingId", blId)),
                        new Document("$set", setDoc),
                        new UpdateOptions().upsert(false)
                ));
            }
        }

        // DELETES
        if (payload.removedItemIds != null && !payload.removedItemIds.isEmpty()) {
            ops.add(new DeleteManyModel<>(
                    and(in("_id", payload.removedItemIds.stream().map(ItemDiffService::toAnyId).collect(Collectors.toList())),
                            eq("billOfLadingId", blId))
            ));
        }

        ApplyDiffResult out = new ApplyDiffResult();
        if (ops.isEmpty()) {
            Log.info("applyItemDiff: nothing to do");
            return out;
        }

        BulkWriteResult res = coll.bulkWrite(ops, new BulkWriteOptions().ordered(false));
        out.inserted = res.getInsertedCount();
        out.modified = res.getModifiedCount();
        out.deleted  = res.getDeletedCount();

        Log.infof("applyItemDiff: inserted=%d modified=%d deleted=%d", out.inserted, out.modified, out.deleted);
        return out;
    }

    private static Object toAnyId(String id) {
        try {
            return new ObjectId(id);
        } catch (Exception e) {
            return id; // if your _id type is String
        }
    }
}
