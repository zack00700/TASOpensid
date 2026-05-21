package fr.alb.engine.contract;

import fr.alb.billing.dao.ContractDao;
import fr.alb.billing.model.Contract;
import fr.alb.billing.model.RateManagement;
import fr.alb.billing.service.RateSelectionService;
import fr.alb.billing.testutil.ContractFixtures;
import fr.alb.model.Event;
import fr.alb.type.FreightKind;
import fr.alb.type.ItemCategory;
import fr.alb.yard.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultContractResolverTest {

    private DefaultContractResolver resolver;
    private ContractDao contractDao;
    private RateSelectionService rateSelector;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new DefaultContractResolver();
        contractDao = Mockito.mock(ContractDao.class);
        rateSelector = Mockito.mock(RateSelectionService.class);
        inject("contractDao", contractDao);
        inject("rateSelector", rateSelector);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field f = DefaultContractResolver.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(resolver, value);
    }

    @Test
    void resolve_returnsEmpty_whenNoActiveContracts() {
        when(contractDao.findActiveContracts()).thenReturn(Collections.emptyList());

        Item item = Mockito.mock(Item.class, Mockito.RETURNS_DEEP_STUBS);
        Event event = Mockito.mock(Event.class, Mockito.RETURNS_DEEP_STUBS);

        Optional<ContractMatch> result = resolver.resolve(item, event, LocalDate.of(2026, 6, 1));
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_returnsEmpty_whenContractsExistButNoneHaveRates() {
        Contract c = ContractFixtures.aContract();
        c.rates = Collections.emptyList();
        when(contractDao.findActiveContracts()).thenReturn(List.of(c));

        Item item = Mockito.mock(Item.class, Mockito.RETURNS_DEEP_STUBS);
        Event event = Mockito.mock(Event.class, Mockito.RETURNS_DEEP_STUBS);

        Optional<ContractMatch> result = resolver.resolve(item, event, LocalDate.of(2026, 6, 1));
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_returnsMatch_whenRateSelectorPicksARate() {
        Contract c = ContractFixtures.aContract();
        RateManagement chosen = c.rates.get(0);
        when(contractDao.findActiveContracts()).thenReturn(List.of(c));
        when(rateSelector.selectRate(any(), any(), any(), any(), any(), any())).thenReturn(chosen);

        Item item = Mockito.mock(Item.class, Mockito.RETURNS_DEEP_STUBS);
        Event event = Mockito.mock(Event.class, Mockito.RETURNS_DEEP_STUBS);

        Optional<ContractMatch> result = resolver.resolve(item, event, LocalDate.of(2026, 6, 1));
        assertTrue(result.isPresent());
        assertSame(c, result.get().contract());
        assertSame(chosen, result.get().rate());
    }

    @Test
    void resolve_threadsItemCategoryAndFreightKind_intoSelectRate() {
        Contract c = ContractFixtures.aContract();
        RateManagement chosen = c.rates.get(0);
        when(contractDao.findActiveContracts()).thenReturn(List.of(c));
        when(rateSelector.selectRate(any(), any(), any(), any(), any(), any())).thenReturn(chosen);

        Item item = Mockito.mock(Item.class);
        when(item.getCategory()).thenReturn(ItemCategory.IMPORT);
        when(item.getFreightKind()).thenReturn(FreightKind.FCL);
        Event event = Mockito.mock(Event.class);

        resolver.resolve(item, event, LocalDate.of(2026, 6, 1));

        ArgumentCaptor<String> categoryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> freightKindCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateSelector).selectRate(
            any(), any(), eq(null), any(),
            categoryCaptor.capture(), freightKindCaptor.capture());
        assertEquals(ItemCategory.IMPORT.getValue(), categoryCaptor.getValue());
        assertEquals(FreightKind.FCL.getValue(), freightKindCaptor.getValue());
    }
}
