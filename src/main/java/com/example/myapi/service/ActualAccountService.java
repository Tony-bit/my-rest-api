package com.example.myapi.service;

import com.example.myapi.dto.ActualAccountDTO;
import com.example.myapi.entity.ActualAccount;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.ActualAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualAccountService {

    private final ActualAccountRepository actualAccountRepository;
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public ActualAccountDTO.Response getResponse() {
        ActualAccount account = actualAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("实盘账户未初始化", 500));
        return ActualAccountDTO.Response.builder()
                .id(account.getId())
                .cashBalance(account.getCashBalance())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    @Transactional
    public ActualAccountDTO.Response updateCashBalance(java.math.BigDecimal newBalance) {
        if (newBalance == null || newBalance.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException("现金余额不能为负数", 400);
        }
        ActualAccount account = actualAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("实盘账户未初始化", 500));
        account.setCashBalance(newBalance);
        ActualAccount saved = actualAccountRepository.save(account);
        log.info("ActualAccount cashBalance updated to {}", newBalance);
        return ActualAccountDTO.Response.builder()
                .id(saved.getId())
                .cashBalance(saved.getCashBalance())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public ActualAccount getAccount() {
        return actualAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("实盘账户未初始化", 500));
    }
}
