package fr.alb.berth.dao;

import fr.alb.berth.model.Hold;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class HoldDaoImpl implements HoldDao {

    @Override
    @Transactional
    public void add(Hold hold) {
        hold.persist();
    }

    @Override
    public Hold findById(String id) {
        return Hold.findById(id);
    }

    @Override
    public List<Hold> findByVisitId(String visitId) {
        return Hold.find("visitId", Sort.descending("openedAt"), visitId).list();
    }

    @Override
    public long countActiveByVisitId(String visitId) {
        return Hold.count("{'visitId': ?1, 'releasedAt': null}", visitId);
    }

    @Override
    @Transactional
    public void update(Hold hold) {
        hold.update();
    }
}
