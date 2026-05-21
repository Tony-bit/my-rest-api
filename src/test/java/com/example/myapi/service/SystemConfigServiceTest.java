package com.example.myapi.service;

import com.example.myapi.dto.SystemConfigDTO;
import com.example.myapi.entity.ActualAccount;
import com.example.myapi.entity.PlanAccount;
import com.example.myapi.entity.SystemConfig;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.ActualAccountRepository;
import com.example.myapi.repository.PlanAccountRepository;
import com.example.myapi.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private PlanAccountRepository planAccountRepository;
    @Mock private ActualAccountRepository actualAccountRepository;

    private SystemConfigService service;

    @BeforeEach
    void setUp() {
        service = new SystemConfigService(systemConfigRepository, planAccountRepository, actualAccountRepository);
    }

    @Test
    void getSystemConfig_noExisting_createsDefault() {
        when(systemConfigRepository.findById(1L)).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any())).thenAnswer(inv -> {
            SystemConfig c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        SystemConfig result = service.getSystemConfig();

        assertEquals(new BigDecimal("500000"), result.getBaselineCapital());
        verify(systemConfigRepository).save(any());
    }

    @Test
    void getSystemConfig_existing_returnsExisting() {
        SystemConfig existing = SystemConfig.builder().baselineCapital(new BigDecimal("800000")).build();
        when(systemConfigRepository.findById(1L)).thenReturn(Optional.of(existing));

        SystemConfig result = service.getSystemConfig();

        assertEquals(new BigDecimal("800000"), result.getBaselineCapital());
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void updateBaselineCapital_increase_rechargesBothAccounts() {
        SystemConfig config = SystemConfig.builder().id(1L).baselineCapital(new BigDecimal("500000")).build();
        PlanAccount planAccount = PlanAccount.builder().id(1L).cashBalance(new BigDecimal("500000")).build();
        ActualAccount actualAccount = ActualAccount.builder().id(1L).cashBalance(new BigDecimal("500000")).build();

        when(systemConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(planAccountRepository.findById(1L)).thenReturn(Optional.of(planAccount));
        when(actualAccountRepository.findById(1L)).thenReturn(Optional.of(actualAccount));
        when(systemConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actualAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SystemConfigDTO.BaselineCapitalResponse resp = service.updateBaselineCapital(new BigDecimal("800000"));

        assertEquals(new BigDecimal("800000"), resp.getBaselineCapital());
        assertEquals(new BigDecimal("800000"), resp.getPlanCashBalance());
        assertEquals(new BigDecimal("800000"), resp.getActualCashBalance());
        assertTrue(resp.getMessage().contains("提高"));
    }

    @Test
    void updateBaselineCapital_decrease_feasible_reducesBothAccounts() {
        SystemConfig config = SystemConfig.builder().id(1L).baselineCapital(new BigDecimal("800000")).build();
        PlanAccount planAccount = PlanAccount.builder().id(1L).cashBalance(new BigDecimal("800000")).build();
        ActualAccount actualAccount = ActualAccount.builder().id(1L).cashBalance(new BigDecimal("800000")).build();

        when(systemConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(planAccountRepository.findById(1L)).thenReturn(Optional.of(planAccount));
        when(actualAccountRepository.findById(1L)).thenReturn(Optional.of(actualAccount));
        when(systemConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actualAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SystemConfigDTO.BaselineCapitalResponse resp = service.updateBaselineCapital(new BigDecimal("500000"));

        assertEquals(new BigDecimal("500000"), resp.getBaselineCapital());
        assertEquals(new BigDecimal("500000"), resp.getPlanCashBalance());
        assertTrue(resp.getMessage().contains("降低"));
    }

    @Test
    void updateBaselineCapital_decreaseInfeasible_throws400() {
        SystemConfig config = SystemConfig.builder().id(1L).baselineCapital(new BigDecimal("800000")).build();
        // planCashBalance (400000) < newBaseline (500000) triggers infeasible exception
        PlanAccount planAccount = PlanAccount.builder().id(1L).cashBalance(new BigDecimal("400000")).build();
        ActualAccount actualAccount = ActualAccount.builder().id(1L).cashBalance(new BigDecimal("400000")).build();

        when(systemConfigRepository.findById(1L)).thenReturn(Optional.of(config));
        when(planAccountRepository.findById(1L)).thenReturn(Optional.of(planAccount));
        when(actualAccountRepository.findById(1L)).thenReturn(Optional.of(actualAccount));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.updateBaselineCapital(new BigDecimal("500000")));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("超过当前现金余额"));
    }

    @Test
    void updateBaselineCapital_zeroOrNegative_throws400() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.updateBaselineCapital(BigDecimal.ZERO));
        assertEquals(400, ex.getStatusCode());

        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                service.updateBaselineCapital(new BigDecimal("-100")));
        assertEquals(400, ex2.getStatusCode());
    }
}
