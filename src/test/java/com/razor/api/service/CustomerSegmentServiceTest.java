package com.razor.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.razor.api.model.Customer;
import com.razor.api.model.CustomerStatus;
import com.razor.api.model.CustomerTag;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.CustomerTagLinkRepository;
import com.razor.api.repository.CustomerTagRepository;
import com.razor.api.tenant.TenantContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Tags e recortes da base (V36)")
class CustomerSegmentServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerService customerService;
    @Mock private CustomerTagRepository tagRepository;
    @Mock private CustomerTagLinkRepository linkRepository;
    @Mock private CustomerStatusService customerStatusService;
    @Mock private TenantAdminService tenantAdminService;

    private final PageRequest pageable = PageRequest.of(0, 20);

    private CustomerTagService tagService() {
        return new CustomerTagService(tagRepository, linkRepository, customerRepository);
    }

    private CustomerSegmentService segments() {
        return new CustomerSegmentService(customerRepository, customerService, tagService(),
                customerStatusService, tenantAdminService);
    }

    private Customer cliente(String phone) {
        return Customer.builder().id(UUID.randomUUID()).barbershopId(TenantContext.getCurrent())
                .phoneNumber(phone).name("C" + phone).build();
    }

    private CustomerTag tag(String name) {
        CustomerTag t = CustomerTag.builder().id(UUID.randomUUID()).barbershopId(TenantContext.getCurrent())
                .name(name).build();
        when(tagRepository.findByIdAndBarbershopId(t.getId(), TenantContext.getCurrent())).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("sem tag nem status a busca é a de sempre")
    void search_withoutSegment_delegates() {
        segments().search("ana", CustomerService.CustomerFilter.ALL, null, null, pageable);
        verify(customerService).searchCustomers("ana", CustomerService.CustomerFilter.ALL, pageable);
    }

    @Test
    @DisplayName("tag + status = interseção dos dois recortes, mandada ao banco")
    void search_intersectsTagAndStatus() {
        Customer a = cliente("1"), b = cliente("2"), c = cliente("3");
        CustomerTag vip = tag("VIP");
        when(linkRepository.findCustomerIdsByTagId(vip.getId())).thenReturn(List.of(a.getId(), b.getId()));
        when(customerStatusService.statuses()).thenReturn(Map.of(
                a.getId(), CustomerStatus.SUMIDO, b.getId(), CustomerStatus.ATIVO, c.getId(), CustomerStatus.SUMIDO));
        when(customerRepository.searchAmong(any(), any(), any(), anyBoolean(), any())).thenReturn(Page.empty());

        segments().search(null, CustomerService.CustomerFilter.ALL, vip.getId(), CustomerStatus.SUMIDO, pageable);

        verify(customerRepository).searchAmong(eq(TenantContext.getCurrent()),
                argThat(ids -> new HashSet<>(ids).equals(Set.of(a.getId()))), eq(null), eq(false), eq(pageable));
    }

    @Test
    @DisplayName("recorte vazio devolve página vazia sem consultar (evita 'in ()')")
    void search_emptySegment_skipsQuery() {
        when(customerStatusService.statuses()).thenReturn(Map.of());

        Page<Customer> out = segments().search(null, CustomerService.CustomerFilter.ALL, null, CustomerStatus.NOVO, pageable);

        assertThat(out).isEmpty();
        verify(customerRepository, never()).searchAmong(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("disparo para o grupo: só quem está no recorte, sem admins")
    void recipients_filtersSegmentAndAdmins() {
        Customer a = cliente("1"), admin = cliente("9"), fora = cliente("3");
        when(customerRepository.findByBarbershopId(any())).thenReturn(List.of(a, admin, fora));
        when(customerStatusService.statuses()).thenReturn(Map.of(
                a.getId(), CustomerStatus.SUMIDO, admin.getId(), CustomerStatus.SUMIDO, fora.getId(), CustomerStatus.ATIVO));
        when(tenantAdminService.adminPhones()).thenReturn(List.of("9"));

        assertThat(segments().recipients(null, CustomerStatus.SUMIDO)).containsExactly(a);
        // Uma leitura so da lista de admins, nao uma consulta por cliente.
        verify(tenantAdminService).adminPhones();
        verify(tenantAdminService, never()).isAdmin(any());
    }

    @Test
    @DisplayName("tag de outra barbearia não é encontrada")
    void tagFromOtherTenant_notFound() {
        UUID alheia = UUID.randomUUID();
        when(tagRepository.findByIdAndBarbershopId(alheia, TenantContext.getCurrent())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> segments().recipients(alheia, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("nome de tag repetido (sem diferenciar maiúsculas) é recusado")
    void createTag_duplicateName_rejected() {
        CustomerTag vip = tag("VIP");
        when(tagRepository.findByBarbershopIdAndNameIgnoreCase(TenantContext.getCurrent(), "vip"))
                .thenReturn(Optional.of(vip));

        assertThatThrownBy(() -> tagService().create("vip", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cor inválida é recusada; sem cor usa o cinza padrão")
    void createTag_color() {
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> tagService().create("Barba", "vermelho")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tagService().create("Barba", null).getColor()).isEqualTo(CustomerTag.DEFAULT_COLOR);
    }

    @Test
    @DisplayName("trocar as tags do cliente apaga as que saíram e cria só as novas")
    void setCustomerTags_replacesSet() {
        Customer c = cliente("1");
        CustomerTag vip = tag("VIP"), barba = tag("Barba"), velha = tag("Velha");
        when(customerRepository.findById(c.getId())).thenReturn(Optional.of(c));
        var linkVip = new com.razor.api.model.CustomerTagLink(c.getId(), vip.getId());
        var linkVelha = new com.razor.api.model.CustomerTagLink(c.getId(), velha.getId());
        when(linkRepository.findByCustomerId(c.getId())).thenReturn(List.of(linkVip, linkVelha));

        var out = tagService().setCustomerTags(c.getId(), List.of(vip.getId(), barba.getId()));

        assertThat(out).extracting(CustomerTag::getName).containsExactly("VIP", "Barba");
        verify(linkRepository).deleteAll(List.of(linkVelha));
        verify(linkRepository).saveAll(argThat(it -> {
            var list = new java.util.ArrayList<com.razor.api.model.CustomerTagLink>();
            it.forEach(list::add);
            return list.size() == 1 && list.get(0).getTagId().equals(barba.getId());
        }));
    }
}
