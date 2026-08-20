package fr.alb.berth.dao;

import fr.alb.berth.model.Hold;

import java.util.List;

public interface HoldDao {

    void add(Hold hold);

    Hold findById(String id);

    List<Hold> findByVisitId(String visitId);

    /** Counts holds with releasedAt == null on the given visit. */
    long countActiveByVisitId(String visitId);

    void update(Hold hold);
}
