package com.razor.api.service;

import com.razor.api.model.Appointment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Fachada da agenda, mantida para os chamadores existentes (bot, comandos do admin, scheduler).
 * NAO tem regra propria: cada metodo so delega para o servico responsavel.
 *
 * <ul>
 *   <li>{@link AgendaAvailabilityService} — horarios livres/ocupados (slots, expediente, bloqueios).</li>
 *   <li>{@link AgendaBookingService} — criar, cancelar e consultar agendamentos do cliente.</li>
 *   <li>{@link AgendaTaskService} — tarefas agendadas (lembrete, avaliacao, disparo, retorno).</li>
 *   <li>{@link AgendaReportService} — textos de agenda/financeiro para o WhatsApp.</li>
 *   <li>{@link LegacyAgendaMigration} — import unico do {@code agenda.json} legado no boot.</li>
 * </ul>
 * Codigo novo deve depender direto do servico especifico; regra nova nao entra aqui.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    private final AgendaAvailabilityService availability;
    private final AgendaBookingService booking;
    private final AgendaTaskService tasks;
    private final AgendaReportService reports;

    // ---- Disponibilidade ----

    public String getHorariosOcupadosParaIA() {
        return availability.getHorariosOcupadosParaIA();
    }

    public String getHorariosDisponiveisDoDia(String date) {
        return availability.getHorariosDisponiveisDoDia(date);
    }

    /** Ocupados para o servico pedido (so barbeiros aptos); {@code null} = barbearia toda. */
    public String getHorariosOcupadosParaIA(ProfessionalAssignmentService.ServicoPedido servico) {
        return servico == null ? availability.getHorariosOcupadosParaIA()
                : availability.getHorariosOcupadosParaIA(servico.nome(), servico.aptos());
    }

    /** Livres do dia para o servico pedido (so barbeiros aptos); {@code null} = barbearia toda. */
    public String getHorariosDisponiveisDoDia(String date, ProfessionalAssignmentService.ServicoPedido servico) {
        return servico == null ? availability.getHorariosDisponiveisDoDia(date)
                : availability.getHorariosDisponiveisDoDia(date, servico.nome(), servico.aptos());
    }

    public String getHorariosDisponiveisDoProfissional(UUID professionalId, String date) {
        return availability.getHorariosDisponiveisDoProfissional(professionalId, date);
    }

    // ---- Agendamentos (criar / cancelar / consultar) ----

    public java.util.Optional<Appointment> adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service) {
        return booking.adicionarAgendamentoViaIA(name, phone, date, time, service);
    }

    public java.util.Optional<Appointment> adicionarAgendamentoViaIA(String name, String phone, String date, String time, String service,
                                          UUID preferredProfessionalId) {
        return booking.adicionarAgendamentoViaIA(name, phone, date, time, service, preferredProfessionalId);
    }

    public int processAgenda(String jsonAgenda) {
        return booking.processAgenda(jsonAgenda);
    }

    public Appointment getAgendamentoMaisRecente(String phone) {
        return booking.getAgendamentoMaisRecente(phone);
    }

    public List<Appointment> getAgendamentosFuturos(String phone) {
        return booking.getAgendamentosFuturos(phone);
    }

    public java.util.Optional<AgendaBookingService.BookingLimitExceeded> checkBookingLimit(String phone) {
        return booking.checkBookingLimit(phone);
    }

    public boolean hasAgendamentoFuturo(String phone) {
        return booking.hasAgendamentoFuturo(phone);
    }

    public boolean cancelarAgendamentoExato(Appointment ag) {
        return booking.cancelarAgendamentoExato(ag);
    }

    public boolean cancelarAgendamentoMaisRecente(String phone) {
        return booking.cancelarAgendamentoMaisRecente(phone);
    }

    // ---- Tarefas agendadas ----

    public void cancelarTarefasClienteExato(String phone) {
        tasks.cancelarTarefasClienteExato(phone);
    }

    public void agendarDisparoMassa(String msg, LocalDateTime execTime) {
        tasks.agendarDisparoMassa(msg, execTime);
    }

    public void agendarNotificacaoRetorno(String phone, int minutesDelay) {
        tasks.agendarNotificacaoRetorno(phone, minutesDelay);
    }

    // ---- Relatorios para o WhatsApp ----

    public String getAgendaSalva() {
        return reports.getAgendaSalva();
    }

    public int getAgendamentosHojeCount() {
        return reports.getAgendamentosHojeCount();
    }

    public String getAgendaHojeText() {
        return reports.getAgendaHojeText();
    }

    public String getPainelAgendamentosDetalhado(long totalBase, long waitlistCount, long pausedCount) {
        return reports.getPainelAgendamentosDetalhado(totalBase, waitlistCount, pausedCount);
    }

    public String getAgendaSemanaText() {
        return reports.getAgendaSemanaText();
    }

    public String getResumoDoDia() {
        return reports.getResumoDoDia();
    }

    /** Nome historico (com o typo) usado pelo scheduler; igual a {@link #getResumoFinanceiroDetalhado()}. */
    public String getResumoDetelhadoDoDia() {
        return reports.getResumoFinanceiroDetalhado();
    }

    public String getResumoFinanceiroDetalhado() {
        return reports.getResumoFinanceiroDetalhado();
    }

    public String getResumoFinanceiroDaSemana() {
        return reports.getResumoFinanceiroDaSemana();
    }
}
