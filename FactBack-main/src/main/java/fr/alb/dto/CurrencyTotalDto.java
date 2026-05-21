package fr.alb.dto;

import java.math.BigDecimal;

public record CurrencyTotalDto(
    String currency,
    BigDecimal total
) {}

