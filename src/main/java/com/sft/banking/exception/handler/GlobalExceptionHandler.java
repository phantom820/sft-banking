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
