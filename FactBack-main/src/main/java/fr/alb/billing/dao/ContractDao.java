package fr.alb.billing.dao;

import java.util.List;

import fr.alb.billing.model.Contract;

public interface ContractDao {

        void addContract(Contract contract);
        List<Contract> getContracts();
        Contract findContract(String contractId);
        List<Contract> findActiveContracts();
        Contract updateContract(Contract contract);
}
