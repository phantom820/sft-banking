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