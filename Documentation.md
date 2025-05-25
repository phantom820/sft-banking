## Bank Account Withdrawal Code Improvements
### Controller
```java
package com.sft.banking.controller;

import com.sft.banking.dto.BankAccountWithdrawalRequest;
import com.sft.banking.dto.BankAccountWithdrawalResponse;
import com.sft.banking.service.BankAccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/{version}/bank")
public class BankAccountController {

  @Autowired
  private BankAccountService bankAccountService;

  @PostMapping("/withdraw")
  public ResponseEntity<BankAccountWithdrawalResponse> withdraw(
          @PathVariable String version,
          @RequestBody @Valid BankAccountWithdrawalRequest request) {
    return ResponseEntity.ok(bankAccountService.withdraw(request));
  }
}

--------

package com.sft.banking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BankAccountWithdrawalRequest(
        @NotNull(message = "accountId is required") @Positive(message = "accountId must be positive") Long accountId,
        @NotNull(message = "amount is required") @Positive(message = "amount must be greater than zero") BigDecimal amount) {
}

-------
        
package com.sft.banking.dto;

import java.math.BigDecimal;

public record BankAccountWithdrawalResponse(String requestId, BigDecimal balance) {
}
```

#### Improvements
- **API Versioning**: Introduced a preceding API version path parameter to endpoints i.e `{version}/bank/...` . This would
  allow API evolution without breaking backwards compatibility.
- **Input Validation**: Defined API request model that ensures that required input is provided and in expected format to avoid passing malformed input
  downstream and doing throw away work.
- **Separation of concerns**: Core business logic should not be implemented in the controller. The controller should
  handle input/output sanitation, routing and should delegate business logic implementation to service layer. All business logic was moved
  out of the controller to the corresponding service.

### Service
```java
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

```

```java
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
```

#### Improvements
Introduced two services `BankAccountService` and `BankAccountEventService`. The `BankAccountService` is responsible for any orchestration and interaction
with persistence layer in fulfilling business requirements. While the `BankAccountEventService` provides mechanism to publish events to SNS. See detailed
improvements for each below.

##### BankAccountService
- **Data Integrity, Correctness & Consistency**: Previous implementation was susceptible to race conditions that could affect correctness and there
was no rollback. New implementation interacts with persistence layer using a transaction that will update both the `BankAccount` and `BankAccountEvent` tables
in the same transaction. This ensures consistency as failure in one with result in rollback for both i.e we cannot have a withdrawal without
a recorded event.
- **Throughput & Fault Tolerance**: The publishing to SNS was done in a blocking fashion and faults in the publish step
would result in overall request failure additionally failure in publishing to SNS leads to dropped events. Now publishing to SNS happens
by periodically reading unpublished events in chronological order from `BankAccountEvent` table and publishing them to SNS, on a successful publish the
record in the table is marked as published (has a `publishedAt` field set), this ensures events will always get delivered
- **Dependency Management**:  Dependency injection is used to supply necessary dependencies (`BankAccountRepository` and `BankAccountEventRepository`) to
  the services, unlike prior code that implemented necessary interaction with database and SNS within itself.
- **Testability**: Business logic workflow is easier to test as service dependency can be mocked and required
  execution paths verified.
- **Observability & Traceability**: Incoming request to service have a `UUID` in form of `requestId` that is used in logging and also
  returned as part of API responses to allow easy tracing of a request.


##### BankAccountEventService
- **Fault Tolerance**: The service periodically scans the `BankAccountEvent` to pickup and send events to SNS, once event is published its state is
updated to indicate it has been published. Events that error while trying to published are not updated and will be picked up again.
- **Observability**: Logging added when a fault is encountered in publishing to SNS, this gives insights into faults
  and makes debugging easier.

  
### Persistence/Repository
```java
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
```

```java
package com.sft.banking.repository;

import com.sft.banking.entity.BankAccountEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface BankAccountEventRepository extends JpaRepository<BankAccountEvent, Long> {

    Page<BankAccountEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable page);
}
```

#### Improvements
For persistence/database interaction introduced `BankAccountRepository` and `BankAccountEventRepository` which are intended to
serve as an interface for interacting with database (CRUD operations). This has the following benefits

- **Flexibility**: Services rely on interfaces for data access instead of having storage specific details embedded in them i.e
prior code had `SQL` queries which exposes implementation details. New approach would allow changing of storage mechanism without
any changes to dependant services as long as interfaces satisfied.
- **Testability**: Code in services that makes use of repositories is easier to test since relies on interfaces and thes can easily be mocked
out for testing.

### Exceptions
```java
package com.sft.banking.exception.handler;

import com.sft.banking.exception.BankAccountNotFoundException;
import com.sft.banking.exception.InsufficientFundsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Unexpected error occurred");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<String> handleInsufficientFunds(InsufficientFundsException ex) {
        log.error(ex.getMessage());
        final String body = String.format("{\"requestId\":\"%s\",\"error\":%s\"}",
                ex.getRequestId(),
                "Insufficient funds, accountId: " + ex.getAccountId());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    @ExceptionHandler(BankAccountNotFoundException.class)
    public ResponseEntity<String> handleBankAccountNotFound(BankAccountNotFoundException ex) {
        log.error(ex.getMessage());
        final String body = String.format("{\"requestId\":\"%s\",\"error\":%s\"}",
                ex.getRequestId(),
                "Account not found, accountId: " + ex.getAccountId());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleInputValidationExceptions(MethodArgumentNotValidException ex) {
        final String requestId = UUID.randomUUID().toString();
        final StringBuilder errorMessages = new StringBuilder();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            errorMessages.append(error.getDefaultMessage()).append(".");
        });
        log.error("Validation errors: {}", errorMessages);

        final String body = String.format("{\"requestId\":\"%s\",\"error\":%s\"}",
                requestId,
                "Validation errors: " + errorMessages);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }
}

---------

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

---------

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
```

#### Improvements
Introduced global exception handler to catch and handle exceptions. This can handle custom exceptions that have been introduced (`InsufficientFundsException` and `BankAccountNotFoundException`) and will default
any other exception to a server internal. Handling exceptions includes logging. This improves

- **Observability**: Exception handlers have logging to indicate what want wrong and makes debugging easier
- **Maintainability**: Centralized place to handle exceptions making code easier to maintain.
- **Data governance**: Generic server internal error returned for unspecified exceptions avoid leaking internal
  implementation details on faults.

### Enviroment Configuration Management
```java
package com.sft.banking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@PropertySource("classpath:application.properties")
@Configuration
public class AwsSnsConfig {

  @Value("${aws.accessKey}")
  private String accessKey;

  @Value("${aws.secretKey}")
  private String secretKey;

  @Value("${aws.region}")
  private String region;

  @Bean
  public SnsClient snsClient() {
    return SnsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
  }
}
```
#### Improvements
Static data required such as `AWS_REGION`, SNS topic arn should be externalized and provided by the
execution environment. This improves

- **Flexibility & Maintainability**: Hardcoded values are avoided as they make code brittle and can be
  expensive to change manually.
- **Portability**: Code is portable as only environment needs to be setup correctly for
  execution i.e can be run in different AWS regions etc

### Summary of overall Improvements
| **Category**             | **Old Code**                                                                                                      | **New Code**                                                                                                                                                                                                      | **Benefit**                                            |   |
|--------------------------|-------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|---|
| Architecture             | Monolithic controller with mixed concerns                                                                         | Clear separation of concerns with different layers Controller, Service, Repository                                                                                                                                | Maintainability, scalability, flexibility              |   |
| Correctness              | Susceptible to race conditions and no rollback mechanism                                                          | Interacts with database using transaction and ensures atomic updates on withdrawal workflow                                                                                                                       | Correctness, data Integrity and avoids race conditions |   |
| Database Efficiency      | Multiple queries in withdrawal flow (queries to retrieve balance and  queries again to update)                    | Single query that checks balance and updates if constraints satisfied                                                                                                                                             | Efficiency                                             |   |
| Exception Handling       | No structured error messages and logging, only returns string messages that do not have appropriate HTTP status codes | Uses centralized exception handling with sufficient logging and returning of structured error messages and appropiate HTTP status codes                                                                           | Observability, auditability                            |   |
| Dependency Management    | Does not make use of dependency injection, instead implements required functionality  inline                      | Makes use of dependency injections and required functionality provided externally                                                                                                                                 | Testability, maintainability and flexibility           |   |
| Throughput               | Reduced throughput due to publishing to to SNS in a blocking fashion                                              | Improved throughput due removing blocking SNS call in handling withdrawal and publishing post saving                                                                                                              | Efficiency, scalability                                |   |
| Input Validation         | No input validation                                                                                               | Input validation in controller to avoid forwarding malformed input downstream                                                                                                                                     | Efficiency, fault tolerance                            |   |
| Code modularity          | Code not modular and mostly implemented in controller                                                             | Code modularized                                                                                                                                                                                                  | Testability, maintainability, flexibility              |   |
| Auditing                 | None                                                                                                              | Introduced requestId that serves as UUID for request served, it can be used to trace execution flow of a request and is also present in events published to SNS so whole flow/lifetime of request can be observed | Observabilty, debugging                                |   |
| Configuration Management | None (Hardcoded AWS region, SNS topic ARN etc)                                                                    | Env variables and config provide externaly using application.properties                                                                                                                                           | Maintainability, portability, testability, security    |   |
| Monitoring               | No monitoring                                                                                                     | Monitoring added (prometheus metrics) and logging                                                                                                                                                                 | Observability                                          |   |
| Versioning               | None                                                                                                              | Introduced a preceding version path parameter for endpoints                                                                                                                                                       | Flexibility                                            |   |                                                                                                                                         