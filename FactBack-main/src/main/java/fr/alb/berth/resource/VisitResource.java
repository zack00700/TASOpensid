package fr.alb.berth.resource;

import fr.alb.berth.model.Visit;
import io.quarkus.mongodb.rest.data.panache.PanacheMongoEntityResource;
import io.quarkus.rest.data.panache.ResourceProperties;

@ResourceProperties(path= "visit")
public interface VisitResource extends PanacheMongoEntityResource<Visit, String> {

}
