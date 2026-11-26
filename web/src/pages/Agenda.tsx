// Agenda interativa (UX_BLUEPRINT §4.6): dia com uma coluna por barbeiro, semana por barbeiro,
// clique no horário vazio para agendar, clique no bloco para detalhes, arrastar para remarcar.
//
// A troca por arrasto é otimista: o bloco muda de lugar na hora e, se o servidor recusar
// (folga, conflito, fora do expediente…), volta para onde estava com o motivo num toast. Quem
// decide é sempre o backend — a tela só antecipa.
import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { Link } from "react-router";
import { ErrorState } from "../components/ErrorState";
import { Layout } from "../components/Layout";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { ScheduleBlocksModal } from "../components/ScheduleBlocksModal";
import { NewAppointmentModal, type NewSlot } from "../components/agenda/NewAppointmentModal";
import { AppointmentDrawer } from "../components/agenda/AppointmentDrawer";
import { Modal, Switch } from "../components/Modal";
import { useToast } from "../components/Toast";
import { useAuth } from "../auth/AuthContext";
import { useApi } from "../lib/useApi";
import { canChangeAppointment, canManageAgenda } from "../lib/access";
import { api, ApiError } from "../api/client";
import type { AgendaBlock, AgendaBoard, AgendaItem, AgendaPro, PaymentStatus } from "../api/types";
import { todayIso } from "../lib/format";
import { isSafePhoto } from "../lib/photo";
import {
  PX_PER_MIN,
  SLOT_MIN,
  addDays,
  gridRange,
  hhmmToMin,
  initials,
  isoWeekday,
  layoutLanes,
  minToHhmm,
  weekStart,
  CONFIRMATION_INFO,
} from "../lib/agenda";
import "./agenda.css";

const PAYMENT_LABEL: Record<PaymentStatus, string> = { PIX: "PIX", PRESENCIAL: "Presencial", PENDENTE: "Pendente" };
const LONG_DATE = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "long" });
const SHORT_DAY = new Intl.DateTimeFormat("pt-BR", { weekday: "short", day: "2-digit" });
const MONTH = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" });

/** Coluna de quem não tem coluna própria: agendamento sem barbeiro ou de barbeiro removido. */
const OTHER = "__other__";
const DRAG_THRESHOLD_PX = 5;

type View = "day" | "week";

interface Column {
  key: string;
  date: string;
  /** Barbeiro da coluna; nulo na coluna "Outros / sem barbeiro". */
  proId: string | null;
  label: string;
  pro?: AgendaPro;
  closed: boolean;
  items: AgendaItem[];
  blocks: AgendaBlock[];
}

interface DragState {
  item: AgendaItem;
  pointerId: number;
  startX: number;
  startY: number;
  /** Distância (min) entre o topo do bloco e o ponto em que foi agarrado. */
  grabOffsetMin: number;
  moved: boolean;
  target: { colKey: string; min: number } | null;
}

function useNarrow(query = "(max-width: 720px)"): boolean {
  const get = () => (typeof window !== "undefined" && window.matchMedia ? window.matchMedia(query).matches : false);
  const [narrow, setNarrow] = useState(get);
  useEffect(() => {
    if (!window.matchMedia) return;
    const mq = window.matchMedia(query);
    const on = () => setNarrow(mq.matches);
    mq.addEventListener?.("change", on);
    return () => mq.removeEventListener?.("change", on);
  }, [query]);
  return narrow;
}

/** Avatar do cabeçalho da coluna. Só vira <img> o que passa no `isSafePhoto` (data URI de imagem). */
function Avatar({ pro, name }: { pro?: AgendaPro; name: string }) {
  const [broken, setBroken] = useState(false);
  const photo = pro?.photoUrl;
  return (
    <span className="cal-avatar" aria-hidden="true">
      {isSafePhoto(photo) && !broken ? <img src={photo} alt="" onError={() => setBroken(true)} /> : initials(name)}
    </span>
  );
}

/** Posição otimista de um bloco remarcado, aplicada por cima do que veio do servidor. */
interface PendingMove {
  date: string;
  time: string;
  professionalId: string;
  professionalName: string | null;
  /**
   * Preenchido quando o servidor aceitou: o `board.data` daquele momento. Assim que chega um
   * `board.data` diferente (a recarga), o servidor volta a ser a verdade e a posição é ignorada.
   */
  settledOn?: AgendaBoard | null;
}

export function Agenda() {
  const toast = useToast();
  const { session } = useAuth();
  // Dono e recepção: a agenda inteira. Barbeiro: só remarca/cancela/marca comparecimento dos
  // PRÓPRIOS horários (profissional ligado ao login) — a API recusa o resto (AgendaPermissions).
  const canEdit = canManageAgenda(session?.role);
  const myProId = session?.role === "BARBER" ? session.professionalId ?? null : null;
  const canChange = (item: AgendaItem) => canChangeAppointment(session, item.professionalId);
  const canDrag = canEdit || !!myProId;
  const narrow = useNarrow();
  // Barra do topo compacta (‹ Hoje › + "⋯"): no celular e também em notebook, onde a fileira
  // inteira (visão, datas, folgas, agendar, relógio) quebrava em duas linhas.
  const compactBar = useNarrow("(max-width: 1360px)");

  const [view, setView] = useState<View>("day");
  const [date, setDate] = useState(todayIso());
  const [weekBarber, setWeekBarber] = useState<string>("");
  const [mobilePro, setMobilePro] = useState<string>("");
  const [showBlocks, setShowBlocks] = useState(false);
  const [newSlot, setNewSlot] = useState<NewSlot | null>(null);
  const [openId, setOpenId] = useState<number | null>(null);
  const [notifyOnDrag, setNotifyOnDrag] = useState(true);
  // Celular: a barra do topo fica em UMA linha (‹ Hoje › + "⋯"); o resto abre numa folha.
  const [sheetOpen, setSheetOpen] = useState(false);

  const from = view === "day" ? date : weekStart(date);
  const to = view === "day" ? date : addDays(from, 6);
  const board = useApi<AgendaBoard>(() => api.get(`/api/admin/agenda?from=${from}&to=${to}`), [from, to]);

  // Trocas otimistas em andamento, por id do agendamento, aplicadas por cima do dado do servidor
  // (sem cópia da lista). Cada troca mexe só na sua entrada — duas trocas simultâneas não se
  // atropelam, e uma recarga no meio não faz o bloco "voltar" antes da resposta.
  const [pending, setPending] = useState<ReadonlyMap<number, PendingMove>>(() => new Map());
  const boardData = useRef<AgendaBoard | null>(null);
  boardData.current = board.data;
  const items = useMemo(() => {
    const base = board.data?.appointments ?? [];
    if (pending.size === 0) return base;
    return base.map((a) => {
      const p = pending.get(a.id);
      if (!p || (p.settledOn !== undefined && p.settledOn !== board.data)) return a;
      return { ...a, date: p.date, time: p.time, professionalId: p.professionalId, professionalName: p.professionalName };
    });
  }, [board.data, pending]);

  const pros = useMemo(() => board.data?.professionals ?? [], [board.data]);
  const activePros = useMemo(() => pros.filter((p) => p.active), [pros]);
  const proById = useMemo(() => new Map(pros.map((p) => [p.id, p])), [pros]);

  const selectedWeekBarber = weekBarber && proById.has(weekBarber) ? weekBarber : activePros[0]?.id ?? "";
  const selectedMobilePro = mobilePro && (proById.has(mobilePro) || mobilePro === OTHER) ? mobilePro : null;

  // ---------------------------------------------------------------- colunas
  const allColumns: Column[] = useMemo(() => {
    const data = board.data;
    if (!data) return [];
    const isOpen = (d: string) => (data.openWeekdays ?? [1, 2, 3, 4, 5, 6]).includes(isoWeekday(d));
    const blocksFor = (d: string, proId: string | null) =>
      data.blocks.filter((b) => b.date === d && (b.professionalId === null || (proId !== null && b.professionalId === proId)));

    if (view === "week") {
      const barber = selectedWeekBarber;
      return Array.from({ length: 7 }, (_, i) => {
        const d = addDays(from, i);
        return {
          key: d,
          date: d,
          proId: barber || null,
          label: SHORT_DAY.format(new Date(`${d}T12:00:00`)),
          pro: proById.get(barber),
          closed: !isOpen(d),
          items: items.filter((a) => a.date === d && a.professionalId === barber),
          blocks: blocksFor(d, barber || null),
        };
      });
    }

    const cols: Column[] = pros.map((p) => ({
      key: p.id,
      date,
      proId: p.id,
      label: p.active ? p.name : `${p.name} (inativo)`,
      pro: p,
      closed: !isOpen(date),
      items: items.filter((a) => a.date === date && a.professionalId === p.id),
      blocks: blocksFor(date, p.id),
    }));
    const orphan = items.filter((a) => a.date === date && (!a.professionalId || !proById.has(a.professionalId)));
    if (orphan.length > 0 || cols.length === 0) {
      cols.push({
        key: OTHER,
        date,
        proId: null,
        label: "Outros / sem barbeiro",
        closed: !isOpen(date),
        items: orphan,
        blocks: blocksFor(date, null),
      });
    }
    return cols;
  }, [board.data, view, from, date, pros, proById, items, selectedWeekBarber]);

  // No celular, a visão do dia mostra um barbeiro por vez (troca por chips ou deslizando).
  const columns = useMemo(() => {
    if (!(narrow && view === "day") || allColumns.length <= 1) return allColumns;
    const key = selectedMobilePro ?? allColumns[0].key;
    return allColumns.filter((c) => c.key === key).length ? allColumns.filter((c) => c.key === key) : [allColumns[0]];
  }, [allColumns, narrow, view, selectedMobilePro]);

  const range = useMemo(
    () => gridRange(board.data?.openingTime ?? null, board.data?.closingTime ?? null, allColumns.flatMap((c) => c.items)),
    [board.data, allColumns],
  );
  const totalMin = range.end - range.start;

  const slots = useMemo(
    () => Array.from({ length: totalMin / SLOT_MIN + 1 }, (_, i) => range.start + i * SLOT_MIN),
    [range, totalMin],
  );

  const visibleCount = allColumns.reduce((n, c) => n + c.items.length, 0);

  // ---------------------------------------------------------------- remarcar (otimista)
  const move = useCallback(
    async (item: AgendaItem, newDate: string, time: string, professionalId: string, notify: boolean): Promise<boolean> => {
      const pro = proById.get(professionalId);
      const entry: PendingMove = {
        date: newDate,
        time: `${time}:00`,
        professionalId,
        professionalName: pro?.name ?? item.professionalName,
      };
      // Mexe só na entrada DESTA troca (se uma troca mais nova do mesmo bloco já a substituiu,
      // deixa quieta). Confirmada: fica valendo até a recarga chegar; recusada: sai na hora.
      const settle = (accepted: boolean) =>
        setPending((prev) => {
          if (prev.get(item.id) !== entry) return prev;
          const next = new Map(prev);
          // Aproveita para limpar as confirmadas que o servidor já substituiu.
          for (const [id, p] of next) {
            if (p.settledOn !== undefined && p.settledOn !== boardData.current) next.delete(id);
          }
          if (accepted) next.set(item.id, { ...entry, settledOn: boardData.current });
          else next.delete(item.id);
          return next;
        });
      setPending((prev) => new Map(prev).set(item.id, entry));
      try {
        await api.patch(`/api/admin/agenda/appointments/${item.id}`, {
          date: newDate,
          time,
          professionalId,
          notifyCustomer: notify,
        });
        toast.success(
          `${item.customerName || "Cliente"} → ${time}${pro ? ` com ${pro.name}` : ""}${notify ? " · cliente avisado" : ""} ✓`,
        );
        settle(true);
        board.reload();
        return true;
      } catch (e) {
        // Desfaz só ESTE bloco: outra troca em andamento não pode ser atropelada.
        settle(false);
        toast.error(e instanceof ApiError ? e.message : "Não foi possível remarcar.");
        return false;
      }
    },
    [proById, toast, board],
  );

  // ---------------------------------------------------------------- arrastar
  const colRefs = useRef(new Map<string, HTMLDivElement>());
  const [drag, setDrag] = useState<DragState | null>(null);
  const dragRef = useRef<DragState | null>(null);
  dragRef.current = drag;

  const targetAt = (clientX: number, clientY: number, d: DragState) => {
    for (const col of columns) {
      const el = colRefs.current.get(col.key);
      if (!el) continue;
      const r = el.getBoundingClientRect();
      if (clientX < r.left || clientX > r.right) continue;
      const raw = range.start + (clientY - r.top) / PX_PER_MIN - d.grabOffsetMin;
      const dur = d.item.durationMinutes || SLOT_MIN;
      const snapped = Math.round(raw / SLOT_MIN) * SLOT_MIN;
      const min = Math.max(range.start, Math.min(range.end - dur, snapped));
      return { colKey: col.key, min };
    }
    return null;
  };

  const onEventPointerDown = (e: ReactPointerEvent<HTMLElement>, item: AgendaItem) => {
    if (e.button !== 0) return;
    // Toque: o gesto é de rolagem/clique. Remarcar no celular é pela gaveta (mais seguro que
    // brigar com a rolagem da página).
    const draggable = canChange(item) && e.pointerType !== "touch" && item.attendanceStatus !== "ATTENDED";
    const top = e.currentTarget.getBoundingClientRect().top;
    const state: DragState = {
      item,
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      grabOffsetMin: (e.clientY - top) / PX_PER_MIN,
      moved: false,
      target: null,
    };
    if (draggable) e.currentTarget.setPointerCapture(e.pointerId);
    setDrag(draggable ? state : { ...state, pointerId: -1 });
  };

  const onEventPointerMove = (e: ReactPointerEvent<HTMLElement>) => {
    const d = dragRef.current;
    if (!d || d.pointerId !== e.pointerId) return;
    const dist = Math.hypot(e.clientX - d.startX, e.clientY - d.startY);
    if (!d.moved && dist < DRAG_THRESHOLD_PX) return;
    setDrag({ ...d, moved: true, target: targetAt(e.clientX, e.clientY, d) });
  };

  const onEventPointerUp = (item: AgendaItem) => {
    const d = dragRef.current;
    setDrag(null);
    if (!d) return;
    if (!d.moved) {
      setOpenId(item.id);
      return;
    }
    const col = columns.find((c) => c.key === d.target?.colKey);
    if (!col || !d.target) return;
    if (!col.proId) {
      toast.error("Solte o horário na coluna de um barbeiro.");
      return;
    }
    if (!canEdit && col.proId !== myProId) {
      toast.error("Você só pode remarcar para a sua própria agenda.");
      return;
    }
    const time = minToHhmm(d.target.min);
    const same = col.date === item.date && time === item.time?.slice(0, 5) && col.proId === item.professionalId;
    if (!same) void move(item, col.date, time, col.proId, notifyOnDrag);
  };

  // Esc solta o bloco sem mexer em nada.
  useEffect(() => {
    if (!drag?.moved) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setDrag(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [drag?.moved]);

  // ---------------------------------------------------------------- deslizar (celular)
  const swipe = useRef<{ x: number; y: number } | null>(null);
  const stepMobilePro = (dir: 1 | -1) => {
    if (allColumns.length <= 1) return;
    const current = columns[0]?.key;
    const idx = Math.max(0, allColumns.findIndex((c) => c.key === current));
    const next = allColumns[(idx + dir + allColumns.length) % allColumns.length];
    setMobilePro(next.key);
  };

  // ---------------------------------------------------------------- navegação
  const isToday = date === todayIso();
  const step = view === "day" ? 1 : 7;
  // Linha do "agora": anda sozinha a cada minuto, mesmo sem nenhuma outra re-renderização.
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(t);
  }, []);
  const nowMin = now.getHours() * 60 + now.getMinutes();
  const showNowIn = (d: string) => d === todayIso() && nowMin >= range.start && nowMin <= range.end;

  const openSlot = (col: Column, min: number) => {
    if (!canEdit || !col.proId || col.closed) return;
    if (!proById.get(col.proId)?.active) return;
    setNewSlot({ date: col.date, time: minToHhmm(min), professionalId: col.proId });
  };

  const quickNew = () => {
    const first = activePros[0];
    if (!first) {
      toast.error("Cadastre um barbeiro ativo em Profissionais para agendar.");
      return;
    }
    const base = isToday ? Math.ceil(nowMin / SLOT_MIN) * SLOT_MIN : range.start;
    setNewSlot({ date, time: minToHhmm(Math.max(range.start, Math.min(base, range.end - SLOT_MIN))), professionalId: first.id });
  };

  const opened = openId !== null ? items.find((a) => a.id === openId) ?? null : null;

  const title =
    view === "day"
      ? LONG_DATE.format(new Date(`${date}T12:00:00`))
      : `Semana de ${MONTH.format(new Date(`${from}T12:00:00`))} a ${MONTH.format(new Date(`${to}T12:00:00`))}`;

  return (
    <Layout
      flush
      title="Agenda"
      actions={
        compactBar ? (
          <div className="chip-row ag-compact">
            <button className="chip-btn" onClick={() => setDate(addDays(date, -step))} aria-label={view === "day" ? "Dia anterior" : "Semana anterior"}>‹</button>
            <button className={`chip-btn ${isToday ? "active" : ""}`} onClick={() => setDate(todayIso())}>Hoje</button>
            <button className="chip-btn" onClick={() => setDate(addDays(date, step))} aria-label={view === "day" ? "Próximo dia" : "Próxima semana"}>›</button>
            <button
              className="chip-btn ag-more"
              onClick={() => setSheetOpen(true)}
              aria-haspopup="dialog"
              aria-label="Filtros e ações da agenda"
              title="Filtros e ações"
            >
              ⋯
            </button>
            {!narrow && canEdit && <button className="btn btn-primary btn-sm" onClick={quickNew}>+ Agendar</button>}
          </div>
        ) : (
          <div className="chip-row">
            <div className="ag-seg" role="group" aria-label="Visão">
              <button className={`chip-btn ${view === "day" ? "active" : ""}`} onClick={() => setView("day")} aria-pressed={view === "day"}>
                Dia
              </button>
              <button className={`chip-btn ${view === "week" ? "active" : ""}`} onClick={() => setView("week")} aria-pressed={view === "week"}>
                Semana
              </button>
            </div>
            <button className="chip-btn" onClick={() => setDate(addDays(date, -step))} aria-label={view === "day" ? "Dia anterior" : "Semana anterior"}>‹</button>
            <button className={`chip-btn ${isToday ? "active" : ""}`} onClick={() => setDate(todayIso())}>Hoje</button>
            <button className="chip-btn" onClick={() => setDate(addDays(date, step))} aria-label={view === "day" ? "Próximo dia" : "Próxima semana"}>›</button>
            <input className="input" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} style={{ width: 158, padding: "6px 10px" }} aria-label="Ir para a data" />
            {canEdit && <button className="chip-btn" onClick={() => setShowBlocks(true)}>🌴 Folgas</button>}
            {canEdit && <button className="btn btn-primary btn-sm" onClick={quickNew}>+ Agendar</button>}
          </div>
        )
      }
    >
      <div className="page-panel">
        <div className="panel-bar">
          <h3>{title}</h3>
          <div className="cal-legend" aria-label="Legenda de cores">
            <span className="lg-item"><i className="lg-dot lg-pix" />PIX</span>
            <span className="lg-item"><i className="lg-dot lg-presencial" />Presencial</span>
            <span className="lg-item"><i className="lg-dot lg-pendente" />Pendente</span>
            <span className="lg-sep" aria-hidden="true" />
            <span className="lg-item"><i className="lg-dot lg-block" />Folga / bloqueio</span>
            <span className="lg-item">💎 Assinante</span>
            <span className="lg-item"><i className="lg-line-now" />Agora</span>
          </div>
          {canDrag && (
            <span className="ag-notify">
              <Switch checked={notifyOnDrag} onChange={setNotifyOnDrag} label="Avisar cliente ao arrastar" />
            </span>
          )}
          <span className="badge badge-muted">{visibleCount} {visibleCount === 1 ? "agendamento" : "agendamentos"}</span>
        </div>

        {view === "week" && activePros.length > 0 && (
          <div className="ag-pro-picker" role="group" aria-label="Barbeiro da semana">
            {pros.map((p) => (
              <button
                key={p.id}
                className={`chip-btn ${selectedWeekBarber === p.id ? "active" : ""}`}
                onClick={() => setWeekBarber(p.id)}
                aria-pressed={selectedWeekBarber === p.id}
              >
                <Avatar pro={p} name={p.name} /> {p.name}
              </button>
            ))}
          </div>
        )}

        {narrow && view === "day" && allColumns.length > 1 && (
          <div className="ag-pro-picker" role="group" aria-label="Barbeiro exibido">
            {allColumns.map((c) => (
              <button
                key={c.key}
                className={`chip-btn ${columns[0]?.key === c.key ? "active" : ""}`}
                onClick={() => setMobilePro(c.key)}
                aria-pressed={columns[0]?.key === c.key}
              >
                {c.pro ? c.pro.name : c.label} <span className="mono muted">{c.items.length}</span>
              </button>
            ))}
          </div>
        )}

        {board.loading && !board.data ? (
          <div className="stack" style={{ padding: 20, gap: 10 }}>
            {[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} h={42} />)}
          </div>
        ) : board.error ? (
          <ErrorState message={`Não foi possível carregar a agenda: ${board.error}`} onRetry={board.reload} />
        ) : pros.length === 0 && visibleCount === 0 ? (
          <EmptyState
            icon="📅"
            title="Nenhum barbeiro cadastrado."
            hint="Cadastre a equipe em Profissionais: cada barbeiro vira uma coluna da agenda."
            action={
              session?.role === "OWNER" ? (
                <Link className="btn btn-primary" to="/professionals">Cadastrar barbeiro</Link>
              ) : undefined
            }
          />
        ) : view === "week" && !selectedWeekBarber ? (
          <EmptyState icon="📅" title="Nenhum barbeiro ativo." hint="A visão da semana é por barbeiro." />
        ) : (
          <div className={`cal${drag?.moved ? " is-dragging" : ""}`}>
            <div className={`cal-head${columns.length === 1 ? " cal-single" : ""}`}>
              <div className="cal-gutter" />
              {columns.map((c) => (
                <div className={`cal-col-head${c.date === todayIso() && view === "week" ? " is-today" : ""}`} key={c.key}>
                  {view === "day" && <Avatar pro={c.pro} name={c.pro?.name ?? "?"} />}
                  <span className="cal-pro">{c.label}</span>
                  <span className="cal-count mono">{c.items.length}</span>
                </div>
              ))}
            </div>

            <div
              className={`cal-body${columns.length === 1 ? " cal-single" : ""}`}
              style={{ height: totalMin * PX_PER_MIN }}
              onTouchStart={(e) => {
                const t = e.touches[0];
                swipe.current = { x: t.clientX, y: t.clientY };
              }}
              onTouchEnd={(e) => {
                const s = swipe.current;
                swipe.current = null;
                if (!s || !(narrow && view === "day")) return;
                const t = e.changedTouches[0];
                const dx = t.clientX - s.x;
                if (Math.abs(dx) > 60 && Math.abs(t.clientY - s.y) < 40) stepMobilePro(dx < 0 ? 1 : -1);
              }}
            >
              <div className="cal-gutter">
                {slots.map((min) => (
                  <div className="cal-time" key={min} style={{ top: (min - range.start) * PX_PER_MIN }}>
                    {min % 60 === 0 ? minToHhmm(min) : ""}
                  </div>
                ))}
              </div>

              {columns.map((c) => {
                const lanes = layoutLanes(c.items);
                const editableCol = canEdit && !!c.proId && !c.closed && !!proById.get(c.proId)?.active;
                const isTarget = drag?.moved && drag.target?.colKey === c.key;
                return (
                  <div
                    className={`cal-col${c.closed ? " is-closed" : ""}${isTarget ? " is-target" : ""}`}
                    key={c.key}
                    ref={(el) => {
                      if (el) colRefs.current.set(c.key, el);
                      else colRefs.current.delete(c.key);
                    }}
                  >
                    {slots.slice(0, -1).map((min) => (
                      // Atalho de mouse: clicar no horário vazio abre o agendamento já preenchido.
                      // No teclado o caminho é o botão "+ Agendar" — por isso é só apresentação.
                      <div
                        key={min}
                        role="presentation"
                        className={`cal-slot ${min % 60 === 0 ? "hour" : ""}${editableCol ? " is-free" : ""}`}
                        style={{ top: (min - range.start) * PX_PER_MIN, height: SLOT_MIN * PX_PER_MIN }}
                        onClick={() => openSlot(c, min)}
                        title={editableCol ? `Agendar às ${minToHhmm(min)}` : undefined}
                      />
                    ))}

                    {c.closed && (
                      <div className="cal-block" style={{ top: 0, height: totalMin * PX_PER_MIN }}>
                        <span>Fechado</span>
                      </div>
                    )}
                    {!c.closed &&
                      c.blocks.map((b) => {
                        const s = hhmmToMin(b.startTime) ?? range.start;
                        const e = hhmmToMin(b.endTime) ?? range.end;
                        const top = Math.max(0, (s - range.start) * PX_PER_MIN);
                        const height = Math.max(0, (Math.min(e, range.end) - Math.max(s, range.start)) * PX_PER_MIN);
                        if (height <= 0) return null;
                        return (
                          <div
                            key={`${b.id}-${b.date}`}
                            className="cal-block"
                            style={{ top, height }}
                            title={`${b.reason || "Bloqueio"}${b.professionalId ? "" : " (barbearia toda)"}`}
                          >
                            <span>{b.reason || "Bloqueado"}</span>
                          </div>
                        );
                      })}

                    {c.items.map((a) => {
                      const startMin = hhmmToMin(a.time);
                      if (startMin === null) return null;
                      const dur = a.durationMinutes || SLOT_MIN;
                      const height = Math.max(dur * PX_PER_MIN - 4, 26);
                      const lane = lanes.get(a.id) ?? { lane: 0, lanes: 1 };
                      const st = a.paymentStatus;
                      const dragging = drag?.moved && drag.item.id === a.id;
                      const att = a.attendanceStatus;
                      return (
                        <div
                          key={a.id}
                          className={[
                            "cal-event",
                            st ? `ev-${st.toLowerCase()}` : "",
                            att === "NO_SHOW" ? "ev-noshow" : "",
                            att === "ATTENDED" ? "ev-done" : "",
                            dragging ? "is-ghost" : "",
                            canChange(a) && att !== "ATTENDED" ? "is-draggable" : "",
                            // Horário curto (15 min): hora e nome na mesma linha, senão o nome sumia.
                            height < 40 ? "ev-short" : "",
                          ].join(" ")}
                          style={{
                            top: (startMin - range.start) * PX_PER_MIN,
                            height,
                            left: `calc(${(lane.lane / lane.lanes) * 100}% + 6px)`,
                            right: "auto",
                            width: `calc(${100 / lane.lanes}% - 12px)`,
                          }}
                          data-id={a.id}
                          tabIndex={0}
                          role="button"
                          aria-label={[
                            a.time?.slice(0, 5),
                            a.customerName || "cliente sem nome",
                            a.subscriber ? "assinante do clube" : null,
                            a.service,
                            st ? PAYMENT_LABEL[st] : null,
                            att === "NO_SHOW" ? "faltou" : att === "ATTENDED" ? "compareceu" : null,
                            !att && a.confirmationStatus ? CONFIRMATION_INFO[a.confirmationStatus].label.toLowerCase() : null,
                            a.depositStatus === "PENDING" ? "sinal pendente" : null,
                            a.professionalName ? `com ${a.professionalName}` : "sem barbeiro",
                            "abrir detalhes",
                          ]
                            .filter(Boolean)
                            .join(", ")}
                          onPointerDown={(e) => onEventPointerDown(e, a)}
                          onPointerMove={onEventPointerMove}
                          onPointerUp={() => onEventPointerUp(a)}
                          onPointerCancel={() => setDrag(null)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                              e.preventDefault();
                              setOpenId(a.id);
                            }
                          }}
                        >
                          <span className="ev-time mono">
                            {a.time?.slice(0, 5)}
                            {att === "ATTENDED" && " ✓"}
                            {att === "NO_SHOW" && " · faltou"}
                            {a.comandaId && " 🧾"}
                            {!att && a.confirmationStatus && (
                              <span title={CONFIRMATION_INFO[a.confirmationStatus].label}>
                                {" "}{CONFIRMATION_INFO[a.confirmationStatus].icon}
                              </span>
                            )}
                            {a.depositStatus === "PENDING" && <span title="Sinal pendente"> 💰</span>}
                          </span>
                          <span className="ev-name">
                            {a.customerName || "—"}
                            {a.subscriber && <span className="ev-gem" title="Assinante do clube">💎</span>}
                          </span>
                          {height > 46 && <span className="ev-service">{a.service}</span>}
                          {height > 68 && st && <span className="ev-pay">{PAYMENT_LABEL[st]}</span>}
                        </div>
                      );
                    })}

                    {isTarget && drag && drag.target && (
                      <div
                        className={`cal-drop${c.proId ? "" : " is-invalid"}`}
                        style={{
                          top: (drag.target.min - range.start) * PX_PER_MIN,
                          height: Math.max((drag.item.durationMinutes || SLOT_MIN) * PX_PER_MIN - 4, 26),
                        }}
                        aria-hidden="true"
                      >
                        <span className="mono">{minToHhmm(drag.target.min)}</span>
                      </div>
                    )}

                    {showNowIn(c.date) && (
                      <div className="cal-now cal-now-col" style={{ top: (nowMin - range.start) * PX_PER_MIN }}>
                        <span className="cal-now-dot" />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>

      {sheetOpen && (
        <Modal title="Agenda" onClose={() => setSheetOpen(false)}>
          <div className="stack ag-sheet">
            <div className="field">
              <span className="field-legend">Visão</span>
              <div className="ag-seg" role="group" aria-label="Visão">
                <button className={`chip-btn ${view === "day" ? "active" : ""}`} onClick={() => setView("day")} aria-pressed={view === "day"}>
                  Dia
                </button>
                <button className={`chip-btn ${view === "week" ? "active" : ""}`} onClick={() => setView("week")} aria-pressed={view === "week"}>
                  Semana
                </button>
              </div>
            </div>
            <div className="field">
              <label>
                Ir para a data
                <input className="input" type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
              </label>
            </div>
            {canDrag && (
              <Switch checked={notifyOnDrag} onChange={setNotifyOnDrag} label="Avisar o cliente ao remarcar" />
            )}
            {canEdit && (
              <div className="row wrap" style={{ gap: 8 }}>
                <button
                  className="btn btn-primary"
                  onClick={() => {
                    setSheetOpen(false);
                    quickNew();
                  }}
                >
                  + Agendar
                </button>
                <button
                  className="btn btn-ghost"
                  onClick={() => {
                    setSheetOpen(false);
                    setShowBlocks(true);
                  }}
                >
                  🌴 Folgas e bloqueios
                </button>
              </div>
            )}
          </div>
        </Modal>
      )}

      {showBlocks && <ScheduleBlocksModal onClose={() => setShowBlocks(false)} onChanged={() => board.reload()} />}

      {newSlot && (
        <NewAppointmentModal
          slot={newSlot}
          pros={pros}
          onClose={() => setNewSlot(null)}
          onCreated={() => {
            setNewSlot(null);
            toast.success("Agendamento criado ✓");
            board.reload();
          }}
        />
      )}

      {opened && (
        <AppointmentDrawer
          key={opened.id}
          item={opened}
          pros={pros}
          canEdit={canChange(opened)}
          canManage={canEdit}
          ownProfessionalId={canEdit ? null : myProId}
          onClose={() => setOpenId(null)}
          onMove={move}
          onChanged={() => board.reload()}
        />
      )}
    </Layout>
  );
}
