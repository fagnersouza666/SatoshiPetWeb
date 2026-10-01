package br.com.satoshipet.api.support;

import org.jboss.logmanager.ExtLogRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Captura mensagem e MDC no instante do log, sem depender do console. */
public final class LogCapture implements AutoCloseable {
    private final List<Entry> entries = new ArrayList<>();
    private final Logger logger;
    private final Level previousLevel;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            ExtLogRecord extended = ExtLogRecord.wrap(record);
            entries.add(new Entry(extended.getFormattedMessage(), extended.getMdc("correlationId")));
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    public LogCapture(Class<?> type) {
        logger = Logger.getLogger(type.getName());
        previousLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
    }

    public record Entry(String message, String correlationId) {}
}
