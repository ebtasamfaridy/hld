package com.logger;

import com.logger.api.Level;
import com.logger.api.Logger;
import com.logger.api.LoggerFactory;
import com.logger.api.MDC;
import com.logger.appender.AsyncAppender;
import com.logger.appender.ConsoleAppender;
import com.logger.appender.FileAppender;
import com.logger.core.LoggerConfig;
import com.logger.core.LoggerConfigBuilder;
import com.logger.core.LoggerContext;
import com.logger.filter.LevelFilter;
import com.logger.filter.RegexFilter;
import com.logger.layout.JsonLayout;
import com.logger.layout.PatternLayout;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) throws Exception {
        ConsoleAppender console = new ConsoleAppender("console", new PatternLayout());
        FileAppender file = new FileAppender("file", new JsonLayout(), Path.of("/tmp/lld-logger.log"));
        AsyncAppender asyncFile = new AsyncAppender("asyncFile", file, 1024, true);

        console.start();
        asyncFile.start();

        // Filter on console: drop "/health" noise.
        console.addFilter(new RegexFilter("GET /health", true));
        console.addFilter(new LevelFilter(Level.DEBUG));

        LoggerConfig cfg = new LoggerConfigBuilder()
                .root(Level.INFO, console)
                .logger("com.app", Level.DEBUG, true,  asyncFile)   // additive: also writes to root's console
                .logger("com.app.web", Level.WARN, false, console)  // non-additive: only console
                .build();

        LoggerFactory.install(new LoggerContext(cfg));

        Logger appLog = LoggerFactory.getLogger("com.app.OrderService");
        Logger webLog = LoggerFactory.getLogger("com.app.web.HealthController");
        Logger lowLog = LoggerFactory.getLogger("com.lib.NoiseyDep");

        try (var ignored = MDC.scoped("requestId", "r-1234")) {
            section("Effective levels");
            System.out.println("  com.app.OrderService → " + appLog.effectiveLevel());
            System.out.println("  com.app.web.HealthController → " + webLog.effectiveLevel());
            System.out.println("  com.lib.NoiseyDep → " + lowLog.effectiveLevel());

            section("Hierarchy & additive");
            appLog.debug("loading user {}", 42);              // file (DEBUG ok); console (level filter drops since console is INFO)
            appLog.info("created order {}", "o-99");           // both
            webLog.warn("slow handler {}ms", 1234);            // only console (additive=false)
            webLog.info("would not appear (level=WARN)");      // dropped at logger
            lowLog.info("noise from dep");                     // root → console

            section("Filter: drop /health from console");
            webLog.warn("GET /health 200 5ms");                // regex deny
            webLog.warn("GET /orders 200 12ms");               // allowed

            section("Reload config: silence app debug");
            LoggerConfig cfg2 = new LoggerConfigBuilder()
                    .root(Level.WARN, console)
                    .logger("com.app", Level.WARN, true, asyncFile)
                    .build();
            LoggerFactory.context().reload(cfg2);
            appLog.info("would not appear after reload");
            appLog.warn("still appears");
        }

        Thread.sleep(200);   // let async drain
        console.close();
        asyncFile.close();
    }

    private static void section(String s) { System.out.println("\n=== " + s + " ==="); }
}
