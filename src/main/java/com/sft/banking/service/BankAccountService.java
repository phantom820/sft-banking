package com.sft.banking.service;

import com.sft.banking.dto.BankAccountWithdrawalRequest;
import com.sft.banking.dto.BankAccountWithdrawalResponse;
import com.sft.banking.entity.BankAccount;
import com.sft.banking.entity.BankAccountEvent;
import com.sft.banking.exception.BankAccountNotFoundException;
import com.sft.banking.exception.InsufficientFundsException;
import com.sft.banking.model.BankWithdrawalEvent;
import com.sft.banking.repository.BankAccountEventRepository;
import com.sft.banking.repository.BankAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class BankAccountService {

    @Autowired
    private BankAccountRepository bankAccountRepository;


    @Autowired
    private BankAccountEventRepository bankAccountEventRepository;

    @Transactional
    public BankAccountWithdrawalResponse withdraw(BankAccountWithdrawalRequest request) {
        final String requestId = UUID.randomUUID().toString();
        final Long accountId = request.accountId();
        final BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new BankAccountNotFoundException(requestId, accountId));
        final BigDecimal balanceBefore = account.getBalance();

        final int affectedRows = bankAccountRepository.withdrawIfSufficientBalance(account.getId(), request.amount());
        if (affectedRows == 0) {
            throw new InsufficientFundsException(requestId, accountId, account.getBalance(), request.amount());
        }

        final BigDecimal balanceAfter = balanceBefore.subtract(request.amount());
        final BankWithdrawalEvent bankWithdrawalEvent = new BankWithdrawalEvent(
                requestId, request.amount(), request.accountId(),
                balanceBefore,  balanceAfter);

        final BankAccountEvent bankAccountEvent = BankAccountEvent.builder()
                .createdAt(LocalDateTime.now())
                .message(bankWithdrawalEvent.toJson())
                .type(BankAccountEvent.Type.WITHDRAWAL)
                .build();

        bankAccountEventRepository.save(bankAccountEvent);

        return new BankAccountWithdrawalResponse(requestId, account.getBalance());
    }
}
