package com.sft.banking.repository;

import com.sft.banking.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    @Transactional
    @Modifying
    @Query("UPDATE BankAccount b SET b.balance = b.balance - :amount WHERE b.id = :accountId AND b.balance >= :amount")
    int withdrawIfSufficientBalance(Long accountId, BigDecimal amount);
}
