package com.sft.banking.repository;

import com.sft.banking.entity.BankAccountEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface BankAccountEventRepository extends JpaRepository<BankAccountEvent, Long> {

    Page<BankAccountEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable page);
}
