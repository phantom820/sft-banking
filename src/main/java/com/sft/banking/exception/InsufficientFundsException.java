package com.sft.banking.exception;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InsufficientFundsException extends RuntimeException {
    private String requestId;
    private Long accountId;
    public InsufficientFundsException(
            String requestId,
            Long accountId,
            BigDecimal balance,
            BigDecimal amount) {
        super("Insufficient funds for withdrawal, accountId: " + accountId + ", balance: " + balance+ ", withdrawalAmount: " + amount);
        this.requestId = requestId;
        this.accountId = accountId;
    }
}