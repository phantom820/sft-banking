package com.sft.banking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BankAccountWithdrawalRequest(
        @NotNull(message = "accountId is required") @Positive(message = "accountId must be positive") Long accountId,
        @NotNull(message = "amount is required") @Positive(message = "amount must be greater than zero") BigDecimal amount) {
}