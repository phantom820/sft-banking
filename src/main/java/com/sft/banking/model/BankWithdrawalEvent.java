package com.sft.banking.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BankWithdrawalEvent {
    private String requestId;
    private BigDecimal amount;
    private Long accountId;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;

    public BankWithdrawalEvent(
            String requestId,
            BigDecimal amount,
            Long accountId,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter) {
        this.requestId = requestId;
        this.amount = amount;
        this.accountId = accountId;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
    }

    public String toJson() {
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing WithdrawalEvent", e);
        }
    }
}