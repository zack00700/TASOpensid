package fr.alb.billing.dao;

import java.util.List;

import fr.alb.billing.model.Payment;
import fr.alb.type.PaymentStatus;

public interface PaymentDao {

    void createPayment(Payment payment);

    Payment findPayment(String paymentId);

    List<Payment> listByCustomer(String customerId, int page, int size);

    List<Payment> listByStatus(PaymentStatus status, int page, int size);

    List<Payment> listAll(int page, int size);

    Payment updatePayment(Payment payment);

    boolean deletePayment(String paymentId);
}
