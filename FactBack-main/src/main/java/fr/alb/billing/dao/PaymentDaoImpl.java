package fr.alb.billing.dao;

import java.util.List;

import fr.alb.billing.model.Payment;
import fr.alb.type.PaymentStatus;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PaymentDaoImpl implements PanacheMongoRepository<Payment>, PaymentDao {

    @Override
    @Transactional
    public void createPayment(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException("Payment cannot be null");
        }
        if (payment.customerId == null || payment.customerId.isBlank()) {
            throw new IllegalArgumentException("Payment customerId cannot be null or blank");
        }
        if (payment.amount == null) {
            throw new IllegalArgumentException("Payment amount cannot be null");
        }
        if (payment.paymentMethod == null) {
            throw new IllegalArgumentException("Payment paymentMethod cannot be null");
        }
        persist(payment);
    }

    @Override
    public Payment findPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId cannot be null or blank");
        }
        return find("_id", paymentId.trim()).firstResult();
    }

    @Override
    public List<Payment> listByCustomer(String customerId, int page, int size) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId cannot be null or blank");
        }
        return find("customerId", customerId.trim())
                .page(Page.of(page - 1, size))
                .list();
    }

    @Override
    public List<Payment> listByStatus(PaymentStatus status, int page, int size) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        return find("status", status)
                .page(Page.of(page - 1, size))
                .list();
    }

    @Override
    public List<Payment> listAll(int page, int size) {
        return findAll()
                .page(Page.of(page - 1, size))
                .list();
    }

    @Override
    @Transactional
    public Payment updatePayment(Payment payment) {
        payment.update();
        return payment;
    }

    @Override
    @Transactional
    public boolean deletePayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("paymentId cannot be null or blank");
        }
        Payment payment = find("_id", paymentId.trim()).firstResult();
        if (payment == null) {
            return false;
        }
        delete(payment);
        return true;
    }
}
