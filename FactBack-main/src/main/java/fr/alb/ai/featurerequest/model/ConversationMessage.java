package fr.alb.ai.featurerequest.model;

import java.time.Instant;

/**
 * A single turn in the AI clarification conversation.
 * Embedded inside FeatureRequest.conversation list.
 */
public class ConversationMessage {

    /** "user" or "assistant" */
    public String role;

    public String content;

    public Instant timestamp;

    public ConversationMessage() {}
}
