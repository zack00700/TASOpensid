package fr.alb.billing.dao;

import java.util.List;

import fr.alb.billing.model.Tariff;
import fr.alb.type.ServiceType;

public interface TariffDao {
    void addTariff(Tariff tariff);
    Tariff findTariff(String tariffId);
    List<Tariff> getActiveTariffs();
    List<Tariff> findByServiceType(ServiceType serviceType);
    Tariff updateTariff(Tariff tariff);
    boolean deleteTariff(String tariffId);
}
