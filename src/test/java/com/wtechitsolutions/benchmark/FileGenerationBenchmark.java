package com.wtechitsolutions.benchmark;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionType;
import com.wtechitsolutions.parser.BeanIOFormatter;
import com.wtechitsolutions.parser.BindyFormatter;
import com.wtechitsolutions.parser.CamelBeanIOFormatter;
import com.wtechitsolutions.parser.FixedFormat4JFormatter;
import com.wtechitsolutions.parser.FixedLengthFormatter;
import com.wtechitsolutions.parser.SpringBatchFormatter;
import com.wtechitsolutions.parser.VelocityFormatter;
import com.wtechitsolutions.strategy.CodaBeanIOStrategy;
import com.wtechitsolutions.strategy.CodaBindyStrategy;
import com.wtechitsolutions.strategy.CodaCamelBeanIOStrategy;
import com.wtechitsolutions.strategy.CodaFixedFormat4JStrategy;
import com.wtechitsolutions.strategy.CodaFixedLengthStrategy;
import com.wtechitsolutions.strategy.CodaSpringBatchStrategy;
import com.wtechitsolutions.strategy.CodaVelocityStrategy;
import com.wtechitsolutions.strategy.FileGenerationStrategy;
import com.wtechitsolutions.strategy.SwiftBeanIOStrategy;
import com.wtechitsolutions.strategy.SwiftBindyStrategy;
import com.wtechitsolutions.strategy.SwiftCamelBeanIOStrategy;
import com.wtechitsolutions.strategy.SwiftFixedFormat4JStrategy;
import com.wtechitsolutions.strategy.SwiftFixedLengthStrategy;
import com.wtechitsolutions.strategy.SwiftSpringBatchStrategy;
import com.wtechitsolutions.strategy.SwiftVelocityStrategy;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmarks for all 14 FileGenerationStrategy implementations
 * (7 libraries x 2 formats), covering both generate and parse operations.
 * 28 @Benchmark methods total.
 *
 * <p>Run via: mvn test -Pbenchmark -Pskip-frontend
 *
 * <p>Results are written to target/jmh-result.json.
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
public class FileGenerationBenchmark {

    private List<Transaction> transactions;
    private List<Account> accounts;

    // CODA strategies
    private FileGenerationStrategy codaBeanIO;
    private FileGenerationStrategy codaFixedFormat4J;
    private FileGenerationStrategy codaFixedLength;
    private FileGenerationStrategy codaBindy;
    private FileGenerationStrategy codaCamelBeanIO;
    private FileGenerationStrategy codaVelocity;
    private FileGenerationStrategy codaSpringBatch;

    // SWIFT strategies
    private FileGenerationStrategy swiftBeanIO;
    private FileGenerationStrategy swiftFixedFormat4J;
    private FileGenerationStrategy swiftFixedLength;
    private FileGenerationStrategy swiftBindy;
    private FileGenerationStrategy swiftCamelBeanIO;
    private FileGenerationStrategy swiftVelocity;
    private FileGenerationStrategy swiftSpringBatch;

    // Pre-generated file content for parse benchmarks
    private String codaBeanIOFile;
    private String codaFf4jFile;
    private String codaVlFile;
    private String codaBindyFile;
    private String codaCamelBeanIOFile;
    private String codaVelocityFile;
    private String codaSpringBatchFile;

    private String swiftBeanIOFile;
    private String swiftFf4jFile;
    private String swiftVlFile;
    private String swiftBindyFile;
    private String swiftCamelBeanIOFile;
    private String swiftVelocityFile;
    private String swiftSpringBatchFile;

    @Setup
    public void setup() {
        accounts = buildAccounts(5);
        transactions = buildTransactions(accounts, 20);

        BeanIOFormatter beanIO = new BeanIOFormatter();
        FixedFormat4JFormatter ff4j = new FixedFormat4JFormatter();
        FixedLengthFormatter vl = new FixedLengthFormatter();

        BindyFormatter bindy = new BindyFormatter();
        bindy.init();

        CamelBeanIOFormatter camelBeanIO = new CamelBeanIOFormatter();
        camelBeanIO.init();

        VelocityFormatter velocity = new VelocityFormatter();
        velocity.init();

        SpringBatchFormatter springBatch = new SpringBatchFormatter();

        codaBeanIO        = new CodaBeanIOStrategy(beanIO);
        codaFixedFormat4J = new CodaFixedFormat4JStrategy(ff4j);
        codaFixedLength   = new CodaFixedLengthStrategy(vl);
        codaBindy         = new CodaBindyStrategy(bindy);
        codaCamelBeanIO   = new CodaCamelBeanIOStrategy(camelBeanIO);
        codaVelocity      = new CodaVelocityStrategy(velocity);
        codaSpringBatch   = new CodaSpringBatchStrategy(springBatch);

        swiftBeanIO        = new SwiftBeanIOStrategy(beanIO);
        swiftFixedFormat4J = new SwiftFixedFormat4JStrategy(ff4j);
        swiftFixedLength   = new SwiftFixedLengthStrategy(vl);
        swiftBindy         = new SwiftBindyStrategy(bindy);
        swiftCamelBeanIO   = new SwiftCamelBeanIOStrategy(camelBeanIO);
        swiftVelocity      = new SwiftVelocityStrategy(velocity);
        swiftSpringBatch   = new SwiftSpringBatchStrategy(springBatch);

        // Pre-generate file content so parse benchmarks measure only parsing
        codaBeanIOFile      = codaBeanIO.generate(transactions, accounts);
        codaFf4jFile        = codaFixedFormat4J.generate(transactions, accounts);
        codaVlFile          = codaFixedLength.generate(transactions, accounts);
        codaBindyFile       = codaBindy.generate(transactions, accounts);
        codaCamelBeanIOFile = codaCamelBeanIO.generate(transactions, accounts);
        codaVelocityFile    = codaVelocity.generate(transactions, accounts);
        codaSpringBatchFile = codaSpringBatch.generate(transactions, accounts);

        swiftBeanIOFile      = swiftBeanIO.generate(transactions, accounts);
        swiftFf4jFile        = swiftFixedFormat4J.generate(transactions, accounts);
        swiftVlFile          = swiftFixedLength.generate(transactions, accounts);
        swiftBindyFile       = swiftBindy.generate(transactions, accounts);
        swiftCamelBeanIOFile = swiftCamelBeanIO.generate(transactions, accounts);
        swiftVelocityFile    = swiftVelocity.generate(transactions, accounts);
        swiftSpringBatchFile = swiftSpringBatch.generate(transactions, accounts);
    }

    // ── CODA generate ─────────────────────────────────────────────────────────

    @Benchmark
    public String codaBeanIO() {
        return codaBeanIO.generate(transactions, accounts);
    }

    @Benchmark
    public String codaFixedFormat4J() {
        return codaFixedFormat4J.generate(transactions, accounts);
    }

    @Benchmark
    public String codaFixedLength() {
        return codaFixedLength.generate(transactions, accounts);
    }

    @Benchmark
    public String codaBindy() {
        return codaBindy.generate(transactions, accounts);
    }

    @Benchmark
    public String codaCamelBeanIO() {
        return codaCamelBeanIO.generate(transactions, accounts);
    }

    @Benchmark
    public String codaVelocity() {
        return codaVelocity.generate(transactions, accounts);
    }

    @Benchmark
    public String codaSpringBatch() {
        return codaSpringBatch.generate(transactions, accounts);
    }

    // ── SWIFT generate ────────────────────────────────────────────────────────

    @Benchmark
    public String swiftBeanIO() {
        return swiftBeanIO.generate(transactions, accounts);
    }

    @Benchmark
    public String swiftFixedFormat4J() {
        return swiftFixedFormat4J.generate(transactions, accounts);
    }

    @Benchmark
    public String swiftFixedLength() {
        return swiftFixedLength.generate(transactions, accounts);
    }

    @Benchmark
    public String swiftBindy() {
        return swiftBindy.generate(transactions, accounts);
    }

    @Benchmark
    public String swiftCamelBeanIO() {
        return swiftCamelBeanIO.generate(transactions, accounts);
    }

    @Benchmark
    public String swiftVelocity() {
        return swiftVelocity.generate(transactions, accounts);
    }

    @Benchmark
    public String swiftSpringBatch() {
        return swiftSpringBatch.generate(transactions, accounts);
    }

    // ── CODA parse ────────────────────────────────────────────────────────────

    @Benchmark
    public List<Transaction> codaBeanIOParse() {
        return codaBeanIO.parse(codaBeanIOFile);
    }

    @Benchmark
    public List<Transaction> codaFixedFormat4JParse() {
        return codaFixedFormat4J.parse(codaFf4jFile);
    }

    @Benchmark
    public List<Transaction> codaFixedLengthParse() {
        return codaFixedLength.parse(codaVlFile);
    }

    @Benchmark
    public List<Transaction> codaBindyParse() {
        return codaBindy.parse(codaBindyFile);
    }

    @Benchmark
    public List<Transaction> codaCamelBeanIOParse() {
        return codaCamelBeanIO.parse(codaCamelBeanIOFile);
    }

    @Benchmark
    public List<Transaction> codaVelocityParse() {
        return codaVelocity.parse(codaVelocityFile);
    }

    @Benchmark
    public List<Transaction> codaSpringBatchParse() {
        return codaSpringBatch.parse(codaSpringBatchFile);
    }

    // ── SWIFT parse ───────────────────────────────────────────────────────────

    @Benchmark
    public List<Transaction> swiftBeanIOParse() {
        return swiftBeanIO.parse(swiftBeanIOFile);
    }

    @Benchmark
    public List<Transaction> swiftFixedFormat4JParse() {
        return swiftFixedFormat4J.parse(swiftFf4jFile);
    }

    @Benchmark
    public List<Transaction> swiftFixedLengthParse() {
        return swiftFixedLength.parse(swiftVlFile);
    }

    @Benchmark
    public List<Transaction> swiftBindyParse() {
        return swiftBindy.parse(swiftBindyFile);
    }

    @Benchmark
    public List<Transaction> swiftCamelBeanIOParse() {
        return swiftCamelBeanIO.parse(swiftCamelBeanIOFile);
    }

    @Benchmark
    public List<Transaction> swiftVelocityParse() {
        return swiftVelocity.parse(swiftVelocityFile);
    }

    @Benchmark
    public List<Transaction> swiftSpringBatchParse() {
        return swiftSpringBatch.parse(swiftSpringBatchFile);
    }

    // ── JUnit 5 entry point ───────────────────────────────────────────────────

    /**
     * JUnit 5 entry point — invoked by {@code mvn test -Pbenchmark}.
     * Runs JMH with JSON output to target/jmh-result.json.
     */
    @Test
    public void runBenchmarks() throws Exception {
        Options opts = new OptionsBuilder()
                .include(FileGenerationBenchmark.class.getSimpleName())
                .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
                .result("target/jmh-result.json")
                .build();
        new Runner(opts).run();
    }

    // ── Data builders ─────────────────────────────────────────────────────────

    private static List<Account> buildAccounts(int count) {
        List<Account> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Account a = new Account();
            a.setId((long) (i + 1));
            a.setAccountNumber(String.format("BE%018d", 100L + i));
            a.setIban(String.format("BE%018d", 100L + i));
            a.setBankCode(String.format("%03d", 310 + i));
            a.setCurrency("EUR");
            a.setBalance(BigDecimal.valueOf(10000L + i * 1000L));
            a.setHolderName("Holder " + i);
            a.setCreatedAt(Instant.now());
            list.add(a);
        }
        return list;
    }

    private static List<Transaction> buildTransactions(List<Account> accounts, int perAccount) {
        List<Transaction> list = new ArrayList<>();
        long id = 1;
        for (Account a : accounts) {
            for (int i = 0; i < perAccount; i++) {
                Transaction t = new Transaction();
                t.setId(id++);
                t.setAccountId(a.getId());
                t.setReference(String.format("REF%012d", id));
                t.setAmount(BigDecimal.valueOf(100L + i * 50L));
                t.setType(i % 2 == 0 ? TransactionType.CREDIT : TransactionType.DEBIT);
                t.setDescription("Benchmark transaction " + i);
                t.setValueDate(LocalDate.now().minusDays(i));
                t.setEntryDate(LocalDate.now().minusDays(i));
                t.setCreatedAt(Instant.now());
                list.add(t);
            }
        }
        return list;
    }
}
