package com.sft.banking.dto;

import java.math.BigDecimal;

public record BankAccountWithdrawalResponse(String requestId, BigDecimal balance) {
}