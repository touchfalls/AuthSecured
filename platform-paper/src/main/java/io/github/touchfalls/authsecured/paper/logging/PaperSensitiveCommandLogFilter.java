package io.github.touchfalls.authsecured.paper.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.message.Message;

import java.util.Locale;

/**
 * Drops Paper's built-in player-command log record before it is formatted, and any other log line
 * that carries a password command with an argument (command spies, chat loggers).
 * Paper logs the raw command before firing PlayerCommandPreprocessEvent, so an
 * event listener alone cannot keep password arguments out of the console.
 */
public final class PaperSensitiveCommandLogFilter extends AbstractFilter implements AutoCloseable {

    private static final String PAPER_COMMAND_MARKER = "issued server command:";
    private final LoggerContext context;
    private final LoggerConfig rootLogger;

    private PaperSensitiveCommandLogFilter(LoggerContext context, LoggerConfig rootLogger) {
        super(Filter.Result.NEUTRAL, Filter.Result.NEUTRAL);
        this.context = context;
        this.rootLogger = rootLogger;
    }

    public static PaperSensitiveCommandLogFilter install() {
        if (!(LogManager.getContext(false) instanceof LoggerContext context)) {
            throw new IllegalStateException("Paper is not using a supported Log4j2 context");
        }
        LoggerConfig rootLogger = context.getConfiguration().getRootLogger();
        PaperSensitiveCommandLogFilter filter = new PaperSensitiveCommandLogFilter(context, rootLogger);
        rootLogger.addFilter(filter);
        context.updateLoggers();
        return filter;
    }

    @Override
    public Result filter(LogEvent event) {
        return containsSensitivePaperCommand(event) ? Result.DENY : Result.NEUTRAL;
    }

    static boolean containsSensitivePaperCommand(LogEvent event) {
        if (event == null) return false;
        Message message = event.getMessage();
        if (message == null) return false;
        String format = message.getFormat();
        if (format == null || !format.toLowerCase(Locale.ROOT).contains(PAPER_COMMAND_MARKER)) {
            return SensitiveCommandLogMatcher.containsCommandWithArgument(message.getFormattedMessage());
        }

        Object[] parameters = message.getParameters();
        if (parameters != null && parameters.length > 0) {
            Object command = parameters[parameters.length - 1];
            if (command != null && SensitiveCommandLogMatcher.isSensitiveCommand(command.toString())) {
                return true;
            }
        }

        String formatted = message.getFormattedMessage();
        int marker = formatted.toLowerCase(Locale.ROOT).indexOf(PAPER_COMMAND_MARKER);
        return marker >= 0 && SensitiveCommandLogMatcher.isSensitiveCommand(
                formatted.substring(marker + PAPER_COMMAND_MARKER.length()));
    }

    @Override
    public void close() {
        rootLogger.removeFilter(this);
        context.updateLoggers();
    }
}
