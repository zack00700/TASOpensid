package fr.alb.startup;

import fr.alb.sequence.service.InvoiceSequenceService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AppStartup {

    private static final Logger LOGGER = Logger.getLogger(AppStartup.class);

    @Inject
    InvoiceSequenceService sequenceService;

    void onStart(@Observes StartupEvent ev) {
        LOGGER.info("Initializing invoice sequences...");
        sequenceService.ensureDefaultSequences();
    }
}
