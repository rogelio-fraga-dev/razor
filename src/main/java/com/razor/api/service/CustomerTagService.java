package com.razor.api.service;

import com.razor.api.model.Customer;
import com.razor.api.model.CustomerTag;
import com.razor.api.model.CustomerTagLink;
import com.razor.api.repository.CustomerRepository;
import com.razor.api.repository.CustomerTagLinkRepository;
import com.razor.api.repository.CustomerTagRepository;
import com.razor.api.tenant.TenantContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tags de cliente (V36): cadastro das tags do tenant e o vinculo com os clientes.
 *
 * <p>Tudo escopado pelo tenant atual: tag ou cliente de outra barbearia responde
 * {@link NotFoundException}, nunca "existe mas nao e seu".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerTagService {

    static final int MAX_NAME_LENGTH = 40;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final CustomerTagRepository tagRepository;
    private final CustomerTagLinkRepository linkRepository;
    private final CustomerRepository customerRepository;

    public List<CustomerTag> list() {
        return tagRepository.findByBarbershopIdOrderByNameAsc(TenantContext.getCurrent());
    }

    public CustomerTag create(String name, String color) {
        UUID shop = TenantContext.getCurrent();
        String nome = validName(name);
        ensureUniqueName(shop, nome, null);
        CustomerTag saved = tagRepository.save(CustomerTag.builder()
                .barbershopId(shop).name(nome).color(validColor(color)).build());
        log.info("[TAGS] Tag '{}' criada no tenant {}", nome, shop);
        return saved;
    }

    public CustomerTag update(UUID id, String name, String color) {
        UUID shop = TenantContext.getCurrent();
        CustomerTag tag = find(id);
        String nome = validName(name);
        ensureUniqueName(shop, nome, id);
        tag.setName(nome);
        tag.setColor(validColor(color));
        return tagRepository.save(tag);
    }

    /** Apaga a tag e tira ela de todos os clientes (os clientes continuam). */
    @Transactional
    public void delete(UUID id) {
        CustomerTag tag = find(id);
        linkRepository.deleteByTagId(tag.getId());
        tagRepository.delete(tag);
        log.info("[TAGS] Tag '{}' apagada no tenant {}", tag.getName(), tag.getBarbershopId());
    }

    /**
     * Troca o conjunto de tags do cliente pelo informado (lista vazia tira todas). Substituir em
     * vez de "adicionar/remover uma" deixa a tela idempotente: reenviar o mesmo pedido nao
     * duplica nem apaga nada a mais.
     */
    @Transactional
    public List<CustomerTag> setCustomerTags(UUID customerId, Collection<UUID> tagIds) {
        UUID shop = TenantContext.getCurrent();
        Customer customer = customerRepository.findById(customerId)
                .filter(c -> shop.equals(c.getBarbershopId()))
                .orElseThrow(() -> new NotFoundException("Cliente nao encontrado."));

        Set<UUID> desejadas = new LinkedHashSet<>(tagIds == null ? List.of() : tagIds);
        Map<UUID, CustomerTag> tags = new HashMap<>();
        for (UUID tagId : desejadas) {
            tags.put(tagId, tagRepository.findByIdAndBarbershopId(tagId, shop)
                    .orElseThrow(() -> new NotFoundException("Tag nao encontrada.")));
        }

        List<CustomerTagLink> atuais = linkRepository.findByCustomerId(customer.getId());
        Set<UUID> jaTem = atuais.stream().map(CustomerTagLink::getTagId).collect(Collectors.toSet());
        linkRepository.deleteAll(atuais.stream().filter(l -> !desejadas.contains(l.getTagId())).toList());
        linkRepository.saveAll(desejadas.stream()
                .filter(t -> !jaTem.contains(t))
                .map(t -> new CustomerTagLink(customer.getId(), t, shop))
                .toList());

        return desejadas.stream().map(tags::get).toList();
    }

    /** Tags de cada cliente informado — uma consulta para a pagina inteira, nao uma por linha. */
    public Map<UUID, List<CustomerTag>> tagsByCustomer(Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return Map.of();
        Map<UUID, CustomerTag> tagsDoTenant = list().stream()
                .collect(Collectors.toMap(CustomerTag::getId, t -> t));
        Map<UUID, List<CustomerTag>> out = new HashMap<>();
        for (CustomerTagLink l : linkRepository.findByCustomerIdIn(customerIds)) {
            CustomerTag tag = tagsDoTenant.get(l.getTagId());
            if (tag == null) continue; // vinculo para tag de outro tenant: nunca deveria existir
            out.computeIfAbsent(l.getCustomerId(), k -> new java.util.ArrayList<>()).add(tag);
        }
        out.values().forEach(lista -> lista.sort(java.util.Comparator.comparing(CustomerTag::getName,
                String.CASE_INSENSITIVE_ORDER)));
        return out;
    }

    /** Ids dos clientes com a tag (a tag precisa ser do tenant atual). */
    public Set<UUID> customerIdsWithTag(UUID tagId) {
        CustomerTag tag = find(tagId);
        return new java.util.HashSet<>(linkRepository.findCustomerIdsByTagId(tag.getId()));
    }

    public CustomerTag find(UUID id) {
        return tagRepository.findByIdAndBarbershopId(id, TenantContext.getCurrent())
                .orElseThrow(() -> new NotFoundException("Tag nao encontrada."));
    }

    private String validName(String name) {
        String nome = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (nome.isEmpty()) throw new IllegalArgumentException("Dê um nome para a tag.");
        if (nome.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("O nome da tag pode ter no máximo " + MAX_NAME_LENGTH + " caracteres.");
        }
        return nome;
    }

    private void ensureUniqueName(UUID shop, String nome, UUID ignoreId) {
        tagRepository.findByBarbershopIdAndNameIgnoreCase(shop, nome)
                .filter(t -> !t.getId().equals(ignoreId))
                .ifPresent(t -> {
                    throw new IllegalArgumentException("Já existe uma tag chamada \"" + t.getName() + "\".");
                });
    }

    private String validColor(String color) {
        if (color == null || color.isBlank()) return CustomerTag.DEFAULT_COLOR;
        if (!COLOR.matcher(color.trim()).matches()) {
            throw new IllegalArgumentException("Cor inválida — use o formato #RRGGBB.");
        }
        return color.trim().toUpperCase();
    }
}
