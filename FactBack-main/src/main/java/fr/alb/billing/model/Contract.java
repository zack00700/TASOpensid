package fr.alb.billing.model;

import fr.alb.model.EntityBase;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import fr.alb.type.Status;
import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.persistence.Column;

@MongoEntity(collection= "CONTRACT")
public class Contract extends EntityBase {

	public static final long serialVersionUID = 1L;
	public String name;
	public String description;

	/**
	 * Calculation strategy. Kept here for backward compatibility.
	 * When tariffId is set, calculationMode is hydrated from the Tariff at load time
	 * by ContractDaoImpl — do not persist both simultaneously.
	 */
	public CalculationMode calculationMode;

	@Column(name="status")
	public Status status;
	public Date startDate;
	public Date endDate;

	/**
	 * Embedded rate schedule. Kept for backward compatibility.
	 * When tariffId is set, rates are hydrated from the Tariff at load time.
	 * Leave null/empty to delegate entirely to the referenced Tariff.
	 */
	public List<RateManagement> rates;

	// ---- N4 extensions ----

	/**
	 * Reference to a Tariff entity.
	 * When set, ContractDaoImpl hydrates rates + calculationMode from the Tariff
	 * unless the Contract already has its own embedded rates.
	 */
	public String tariffId;

	/** Customer identifier (customerId from the customer registry). */
	public String customerId;

	/** Denormalized customer name for display without a join. */
	public String customerName;

	/**
	 * Resolution priority when multiple contracts match an item.
	 * Higher value = higher priority. Default 0 (lowest).
	 */
	public int priority;

	/**
	 * Versioned amendments to this contract.
	 * An addendum can override rates for a specific date window without
	 * replacing the base contract.
	 */
	public List<ContractAddendum> addendums;

	public Contract() {
		super();
		this.status = Status.ACTIVE;
		this.priority = 0;
		this.addendums = new ArrayList<>();
	}

	public Contract(String name, String description, CalculationMode calculationMode, Status status, Date startDate,
			Date endDate, List<RateManagement> rates) {
		super();
		this.name = name;
		this.description = description;
		this.calculationMode = calculationMode;
		this.status = status;
		this.startDate = startDate;
		this.endDate = endDate;
		this.rates = rates;
		this.priority = 0;
		this.addendums = new ArrayList<>();
	}

	@Override
	public String toString() {
		return "Contract [name=" + name + ", description=" + description + ", calculationMode=" + calculationMode
				+ ", status=" + status + ", startDate=" + startDate + ", endDate=" + endDate
				+ ", tariffId=" + tariffId + ", customerId=" + customerId + ", priority=" + priority + "]";
	}
}
