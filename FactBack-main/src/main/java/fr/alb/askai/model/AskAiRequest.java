package fr.alb.askai.model;

import jakarta.validation.constraints.NotBlank;

public class AskAiRequest {

    @NotBlank
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
