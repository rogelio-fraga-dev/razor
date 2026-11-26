package com.razor.api.platform;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda em memória os WARN/ERROR do backend, agrupados pela "assinatura" da mensagem (números,
 * telefones e ids trocados por #), para o relatório do console (26/11). Some no restart — o
 * objetivo é o "o que está dando errado agora", não auditoria.
 */
@Component
public class ErrorLogBuffer {

    private static final int MAX_GROUPS = 300;
    private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

    public static final class Group {
        public final String level, logger, signature;
        public String example;
        public int count;
        public LocalDateTime first, last;

        Group(String level, String logger, String signature) {
            this.level = level;
            this.logger = logger;
            this.signature = signature;
        }
    }

    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    @PostConstruct
    void attach() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) return;
        AppenderBase<ILoggingEvent> app = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent e) {
                if (!e.getLevel().isGreaterOrEqual(Level.WARN)) return;
                if (!e.getLoggerName().startsWith("com.razor")) return;
                record(e);
            }
        };
        app.setContext(ctx);
        app.setName("razor-error-buffer");
        app.start();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(app);
    }

    static String signature(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "#id")
                .replaceAll("\\d{4,}", "#").replaceAll("\\d+", "#")
                .replaceAll("'[^']{0,80}'", "'…'")
                .trim();
    }

    private void record(ILoggingEvent e) {
        String msg = e.getFormattedMessage();
        IThrowableProxy t = e.getThrowableProxy();
        if (t != null) msg = msg + " — " + t.getClassName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
        String logger = e.getLoggerName().replace("com.razor.api.", "");
        String sig = signature(msg);
        String key = e.getLevel() + "|" + logger + "|" + sig;
        if (!groups.containsKey(key) && groups.size() >= MAX_GROUPS) return;
        LocalDateTime when = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.getTimeStamp()), BR);
        Group g = groups.computeIfAbsent(key, k -> {
            Group n = new Group(e.getLevel().toString(), logger, sig);
            n.first = when;
            return n;
        });
        synchronized (g) {
            g.count++;
            g.last = when;
            g.example = mask(msg.length() > 400 ? msg.substring(0, 400) : msg);
        }
    }

    /** Telefones e e-mails mascarados no exemplo (o relatório sai do sistema). */
    static String mask(String s) {
        return s.replaceAll("(\\d{4})\\d{5,}(\\d{2})", "$1•••••$2")
                .replaceAll("([\\w.+-]{2})[\\w.+-]*@", "$1•••@");
    }

    public List<Group> snapshot() {
        List<Group> out = new ArrayList<>(groups.values());
        out.sort((a, b) -> Integer.compare(b.count, a.count));
        return out;
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groups", groups.size());
        m.put("events", groups.values().stream().mapToInt(g -> g.count).sum());
        return m;
    }
}
