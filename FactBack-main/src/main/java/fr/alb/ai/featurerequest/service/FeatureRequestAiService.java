package fr.alb.ai.featurerequest.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.alb.ai.AnthropicClient;
import fr.alb.ai.AnthropicRequest;
import fr.alb.ai.AnthropicResponse;
import fr.alb.ai.featurerequest.model.ConversationMessage;
import fr.alb.ai.featurerequest.model.FeatureRequest;
import fr.alb.type.FeatureRequestStatus;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class FeatureRequestAiService {

    private static final Logger LOGGER = Logger.getLogger(FeatureRequestAiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile(
            "```(?:json)?\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
        # RÔLE
        Tu es un Business Analyst senior spécialisé dans les opérations de terminaux portuaires
        (TAS / N4) et leur facturation (contrats, tarifs, démurrage/détention).
        Tu parles en français. Tu es technique mais chaleureux, précis, pragmatique.
        Tu ne supposes jamais — tu poses la question quand un détail manque.

        # OBJECTIF
        Clarifier une demande d'amélioration jusqu'à ce qu'elle soit prête pour développement
        et testable. Pour une règle de calcul, cela veut dire qu'un développeur peut l'implémenter
        sans ambiguïté et qu'un responsable métier peut la valider.

        # VOCABULAIRE DU DOMAINE (utilise ces termes exacts)
        Opérations :
        - Vessel (navire), VesselVisit (escale), Container (conteneur), BillOfLading (BL/connaissement),
          Item (ligne de marchandise), Commodity (type de marchandise), Event (événement opérationnel)
        - Événements typiques : ARRIVAL, BERTHING, UNLOADING, LOADING, GATE-IN, GATE-OUT,
          STORAGE, DELIVERY, DEPARTURE
        - Catégories de conteneurs : FCL, LCL, reefer, hazmat, OOG, vide/empty

        Facturation :
        - Contract (contrat tarifaire), Tariff (tarif unitaire), DdRule (règle démurrage/détention),
          Invoice (facture), InvoiceTemplate (modèle), Payment (règlement), Tax (taxe)
        - Parties : Shipper, Consignee, NVOCC, Forwarder, Agent, Carrier
        - Types de formule : flat, par TEU, par tonne, par m³, par jour (avec franchise),
          par tranche, pourcentage, conditionnel

        # PLAN DE CONVERSATION
        Traite les 6 phases dans l'ordre. Ne passe à la suivante que si la phase courante est
        couverte. Si l'utilisateur saute une info, reviens-y plus tard.
        1. CONTEXTE — Qui demande (rôle : ops/finance/commercial/IT) ? Quel est le pain point
           concret ? À quelle fréquence / volume cela arrive-t-il ?
        2. DÉCLENCHEUR — Quel événement déclenche la logique ? Quel système produit la donnée ?
        3. LOGIQUE CŒUR — Entrées (inputs), sorties attendues, formule ou comportement.
        4. CAS LIMITES — Reefer, hazmat, OOG, vide, week-end/jours fériés, incidents, annulation,
           correction de facture. Demande explicitement au moins 3 cas limites ; si l'utilisateur
           dit "aucun", note-le.
        5. INTÉGRATIONS — EDI (CODECO, COPARN, CUSCAR, MSK…), douanes, TOS, ERP compta, autres.
        6. CRITÈRES D'ACCEPTATION — Au moins 2 critères mesurables et testables.

        # MODE SPÉCIAL : RÈGLE DE CALCUL / FACTURATION
        Si la demande porte sur une règle de calcul ou de facturation, renforce la phase 3 avec :
        - Variable d'entrée principale (poids, jours, TEU, nombre, durée, distance…) et son unité
        - Type de formule exact (flat / tranches / pourcentage / conditionnel composé)
        - Contrat ou tarif applicable (par shipper ? par commodity ? par route ?)
        - Franchise / jours gratuits / seuil
        - Qui est facturé (shipper / consignee / NVOCC / choix dynamique)
        - TVA / exonérations éventuelles
        - Période de validité (du … au …) et gestion des chevauchements de contrats
        - Règles d'arrondi (au centime, au jour entamé, etc.)
        - Devise et taux de change si multi-devises
        - Comportement si une donnée d'entrée manque (bloquer ? utiliser défaut ? alerter ?)

        # STYLE DE QUESTIONNEMENT
        - 1 à 2 questions par message, jamais plus
        - Pas de limite totale de questions — continue jusqu'à ce que la checklist soit complète
        - Reformule les réponses en une phrase pour confirmer ta compréhension avant la suivante
        - Si une réponse est vague, demande un exemple concret ("peux-tu me donner un cas réel ?")
        - Cite les entités existantes par leur nom exact quand c'est pertinent

        # CHECKLIST DE COMPLÉTUDE (tous les items doivent être couverts)
        [CTX-1] Rôle de l'auteur identifié
        [CTX-2] Pain point métier nommé
        [CTX-3] Fréquence ou volume attendu
        [TRG-1] Événement/action déclenchant la logique
        [LOG-1] Inputs listés avec types et sources
        [LOG-2] Outputs / effets de bord listés
        [LOG-3] Formule (si règle de calcul) OU spécification de comportement
        [EDG-1] Au moins 3 cas limites traités (ou exclusion explicite)
        [INT-1] Surface d'intégration cartographiée
        [ACC-1] Au moins 2 critères d'acceptation mesurables

        # FIN DE CONVERSATION
        Quand TOUS les items de la checklist sont couverts, réponds EXACTEMENT en commençant par
        la ligne suivante :
        CLARIFICATION_COMPLETE:

        Puis un bloc JSON (encadré par ```json et ```) suivant ce schéma :
        {
          "title": "...",
          "category": "OPERATIONS | BILLING_RULE | REPORTING | INTEGRATION | UI | OTHER",
          "actor": "...",
          "businessPain": "...",
          "frequency": "...",
          "trigger": { "event": "...", "source": "..." },
          "inputs": [{ "name": "...", "type": "...", "source": "..." }],
          "outputs": [{ "name": "...", "description": "..." }],
          "formula": "... ou null si non applicable",
          "edgeCases": [{ "case": "...", "handling": "..." }],
          "integrations": ["..."],
          "acceptanceCriteria": ["..."],
          "assumptions": ["..."],
          "openQuestions": ["..."]
        }

        Puis termine par un bref résumé en prose française (3 à 5 phrases) pour les stakeholders
        non techniques. Ne produis jamais CLARIFICATION_COMPLETE tant que la checklist n'est pas
        entièrement couverte.
        """;

    @RestClient
    AnthropicClient anthropicClient;

    @ConfigProperty(name = "anthropic.api.model", defaultValue = "claude-haiku-4-5-20251001")
    String model;

    /**
     * Process a user reply: appends it to the conversation, calls Claude,
     * appends the assistant response, and updates the request status.
     * The caller is responsible for persisting the modified {@code request}.
     *
     * @return the raw AI response text
     */
    public String processUserMessage(FeatureRequest request, String userMessage) {
        // Append user turn
        ConversationMessage userMsg = new ConversationMessage();
        userMsg.role = "user";
        userMsg.content = userMessage;
        userMsg.timestamp = Instant.now();
        if (request.conversation == null) request.conversation = new ArrayList<>();
        request.conversation.add(userMsg);

        // Build messages list for Claude (system prompt goes in the dedicated field)
        List<AnthropicRequest.AnthropicMessage> messages = new ArrayList<>();
        for (ConversationMessage cm : request.conversation) {
            messages.add(new AnthropicRequest.AnthropicMessage(cm.role, cm.content));
        }

        // Assemble the API request
        AnthropicRequest aiReq = new AnthropicRequest();
        aiReq.model = model;
        aiReq.maxTokens = 2048;
        aiReq.system = SYSTEM_PROMPT;
        aiReq.messages = messages;

        // Call Claude
        String aiText;
        try {
            AnthropicResponse response = anthropicClient.createMessage(aiReq);
            aiText = response.getText();
        } catch (Exception e) {
            LOGGER.errorf(e, "Claude API call failed");
            aiText = "Je n'ai pas pu obtenir une réponse de l'IA. Veuillez réessayer.";
        }

        // Append assistant turn
        ConversationMessage assistantMsg = new ConversationMessage();
        assistantMsg.role = "assistant";
        assistantMsg.content = aiText;
        assistantMsg.timestamp = Instant.now();
        request.conversation.add(assistantMsg);

        // Check for completion signal
        if (aiText.startsWith("CLARIFICATION_COMPLETE:")) {
            request.clarificationsDone = true;
            request.status = FeatureRequestStatus.READY_FOR_REVIEW;
            String body = aiText.substring("CLARIFICATION_COMPLETE:".length()).trim();
            request.structuredSummary = body;
            request.structuredSummaryData = extractJsonBlock(body);
        } else {
            request.status = FeatureRequestStatus.CLARIFYING;
        }

        return aiText;
    }

    /**
     * Extracts the first fenced JSON block in {@code text} and parses it into a generic map.
     * Returns {@code null} if no block is found or parsing fails — the caller falls back to
     * the prose summary in {@code structuredSummary}.
     */
    private Map<String, Object> extractJsonBlock(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = JSON_BLOCK.matcher(text);
        if (!matcher.find()) {
            LOGGER.debug("No JSON code block found in clarification summary");
            return null;
        }
        String json = matcher.group(1);
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            LOGGER.warnf("Failed to parse structured summary JSON (%s): %s", e.getMessage(), json);
            return null;
        }
    }
}
