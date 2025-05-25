package com.sft.banking.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BankAccountNotFoundException extends RuntimeException {
    private String requestId;
    private Long accountId;
    public BankAccountNotFoundException(String requestId, Long accountId) {
        super(String.format("Bank account not found, accountId: %d",accountId));
        this.requestId = requestId;
        this.accountId = accountId;
    }
}
