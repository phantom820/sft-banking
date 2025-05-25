package com.sft.banking.service;


import com.sft.banking.entity.BankAccountEvent;
import com.sft.banking.repository.BankAccountEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BankAccountEventService {

    private static final Logger log = LoggerFactory.getLogger(BankAccountEventService.class);

    private static final int PAGE_SIZE = 100;

    @Autowired
    private SnsClient snsClient;

    @Autowired
    private BankAccountEventRepository bankAccountEventRepository;

    @Value("${aws.sns.topic.bank-account-event}")
    private String bankAccountEventTopicArn;

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {

        final Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        Page<BankAccountEvent> page;

        do {
            page = bankAccountEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(pageable);
            final List<BankAccountEvent> unpublishedEvents = page.getContent();

            for (final BankAccountEvent bankAccountEvent: unpublishedEvents) {
                try {
                    final PublishRequest publishRequest = PublishRequest.builder()
                            .topicArn(bankAccountEventTopicArn)
                            .message(bankAccountEvent.getMessage())
                            .build();

                    snsClient.publish(publishRequest);
                    bankAccountEvent.setPublishedAt(LocalDateTime.now());
                    bankAccountEventRepository.save(bankAccountEvent);
                } catch (final Exception e) {
                    log.error("Failed to publish event: {}", bankAccountEvent, e);
                }
            }
            bankAccountEventRepository.flush();
        } while (page.hasNext());

    }
}
