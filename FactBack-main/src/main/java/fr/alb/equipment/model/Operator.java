package fr.alb.equipment.model;

import fr.alb.model.EntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * A terminal employee authorised to operate equipment.
 *
 * <p>Certifications carry the subset of equipment types an operator can
 * work with. A {@link Shift} binds an operator to a time window.
 */
@MongoEntity(collection = "OPERATOR")
public class Operator extends EntityBase {

    public static final long serialVersionUID = 1L;

    /** Employee code (internal HR reference). */
    public String employeeCode;

    public String firstName;
    public String lastName;

    /**
     * Link to the optional auth principal (User) in platform. Not every
     * operator has a login — yard workers may be tracked without one.
     */
    public String userId;

    /**
     * Types this operator is certified to operate.
     * Strings like "QC", "RTG", "REACH_STACKER" — kept as String to avoid
     * coupling to {@link Crane.CraneType} / {@link YardMachine.MachineType}
     * enums, so new equipment types can be added without schema migration.
     */
    public List<String> certifications;

    /** Date the last refresher training was validated. */
    public LocalDate lastTrainingDate;

    public boolean active = true;
}
