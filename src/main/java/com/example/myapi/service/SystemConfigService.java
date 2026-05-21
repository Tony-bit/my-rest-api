package com.example.myapi.service;

import com.example.myapi.dto.SystemConfigDTO;
import com.example.myapi.entity.ActualAccount;
import com.example.myapi.entity.PlanAccount;
import com.example.myapi.entity.SystemConfig;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.ActualAccountRepository;
import com.example.myapi.repository.PlanAccountRepository;
import com.example.myapi.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final PlanAccountRepository planAccountRepository;
    private final ActualAccountRepository actualAccountRepository;

    @PostConstruct
    @Transactional
    public void initializeAccounts() {
        SystemConfig config = getSystemConfig();
        BigDecimal baseline = config.getBaselineCapital();

        if (planAccountRepository.count() == 0) {
            PlanAccount account = PlanAccount.builder()
                    .cashBalance(baseline)
                    .build();
            planAccountRepository.save(account);
            log.info("Created PlanAccount with cashBalance={}", baseline);
        }

        if (actualAccountRepository.count() == 0) {
            ActualAccount account = ActualAccount.builder()
                    .cashBalance(baseline)
                    .build();
            actualAccountRepository.save(account);
            log.info("Created ActualAccount with cashBalance={}", baseline);
        }
    }

    @Transactional(readOnly = true)
    public SystemConfig getSystemConfig() {
        return systemConfigRepository.findById(1L)
                .orElseGet(() -> {
                    SystemConfig created = SystemConfig.builder()
                            .baselineCapital(new BigDecimal("500000"))
                            .build();
                    return systemConfigRepository.save(created);
                });
    }

    @Transactional(readOnly = true)
    public SystemConfigDTO.Response getResponse() {
        SystemConfig config = getSystemConfig();
        return SystemConfigDTO.Response.builder()
                .id(config.getId())
                .baselineCapital(config.getBaselineCapital())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    @Transactional
    public SystemConfigDTO.BaselineCapitalResponse updateBaselineCapital(BigDecimal newBaseline) {
        if (newBaseline == null || newBaseline.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("基准资金必须大于 0", 400);
        }

        SystemConfig config = getSystemConfig();
        BigDecimal currentBaseline = config.getBaselineCapital();

        PlanAccount planAccount = planAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("预案账户未初始化", 500));
        ActualAccount actualAccount = actualAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("实盘账户未初始化", 500));

        if (newBaseline.compareTo(currentBaseline) < 0) {
            if (newBaseline.compareTo(planAccount.getCashBalance()) > 0) {
                throw new BusinessException(
                        String.format("新基准 %.2f 元超过当前现金余额 %.2f 元，无法降低",
                                newBaseline, planAccount.getCashBalance()), 400);
            }
        }

        planAccount.setCashBalance(newBaseline);
        actualAccount.setCashBalance(newBaseline);
        config.setBaselineCapital(newBaseline);

        planAccountRepository.save(planAccount);
        actualAccountRepository.save(actualAccount);
        systemConfigRepository.save(config);

        String message;
        if (newBaseline.compareTo(currentBaseline) > 0) {
            BigDecimal delta = newBaseline.subtract(currentBaseline);
            message = String.format("基准资金已从 %.2f 提高到 %.2f，两个账户已同步充值 %.2f 元",
                    currentBaseline, newBaseline, delta);
        } else {
            message = String.format("基准资金已从 %.2f 降低到 %.2f，两个账户现金已同步回拨",
                    currentBaseline, newBaseline);
        }

        log.info("Baseline capital updated: {} -> {}", currentBaseline, newBaseline);

        return SystemConfigDTO.BaselineCapitalResponse.builder()
                .baselineCapital(newBaseline)
                .planCashBalance(planAccount.getCashBalance())
                .actualCashBalance(actualAccount.getCashBalance())
                .message(message)
                .build();
    }

    @Transactional(readOnly = true)
    public PlanAccount getPlanAccount() {
        return planAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("预案账户未初始化", 500));
    }

    @Transactional(readOnly = true)
    public ActualAccount getActualAccount() {
        return actualAccountRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("实盘账户未初始化", 500));
    }

    @Transactional
    public void updatePlanCashBalance(BigDecimal newBalance) {
        PlanAccount account = getPlanAccount();
        account.setCashBalance(newBalance);
        planAccountRepository.save(account);
    }

    @Transactional
    public void updateActualCashBalance(BigDecimal newBalance) {
        ActualAccount account = getActualAccount();
        account.setCashBalance(newBalance);
        actualAccountRepository.save(account);
    }

    @Transactional
    public void deductPlanCashBalance(BigDecimal amount) {
        PlanAccount account = getPlanAccount();
        account.setCashBalance(account.getCashBalance().subtract(amount));
        planAccountRepository.save(account);
    }

    @Transactional
    public void addPlanCashBalance(BigDecimal amount) {
        PlanAccount account = getPlanAccount();
        account.setCashBalance(account.getCashBalance().add(amount));
        planAccountRepository.save(account);
    }

    @Transactional
    public void deductActualCashBalance(BigDecimal amount) {
        ActualAccount account = getActualAccount();
        account.setCashBalance(account.getCashBalance().subtract(amount));
        actualAccountRepository.save(account);
    }

    @Transactional
    public void addActualCashBalance(BigDecimal amount) {
        ActualAccount account = getActualAccount();
        account.setCashBalance(account.getCashBalance().add(amount));
        actualAccountRepository.save(account);
    }
}
