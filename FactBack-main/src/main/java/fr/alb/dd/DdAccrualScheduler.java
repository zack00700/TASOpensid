package fr.alb.dd;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DdAccrualScheduler {

    private static final Logger LOGGER = Logger.getLogger(DdAccrualScheduler.class);

    @Inject
    DdAccrualService accrualService;

    @Scheduled(cron = "${app.dd.scheduler.cron:off}")
    public void tick() {
        LOGGER.info("DdAccrualScheduler: starting daily tick");
        try {
            int updated = accrualService.tickAll();
            LOGGER.infof("DdAccrualScheduler: updated %d accruals", updated);
        } catch (Exception e) {
            LOGGER.errorf(e, "DdAccrualScheduler: tick failed");
        }
    }
}
