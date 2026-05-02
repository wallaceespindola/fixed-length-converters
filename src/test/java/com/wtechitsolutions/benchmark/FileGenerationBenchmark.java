package com.wtechitsolutions.benchmark;

import com.wtechitsolutions.domain.Account;
import com.wtechitsolutions.domain.FileType;
import com.wtechitsolutions.domain.Library;
import com.wtechitsolutions.domain.Transaction;
import com.wtechitsolutions.domain.TransactionType;
import com.wtechitsolutions.parser.BeanIOFormatter;
import com.wtechitsolutions.parser.BindyFormatter;
import com.wtechitsolutions.parser.FixedFormat4JFormatter;
import com.wtechitsolutions.parser.FixedLengthFormatter;
import com.wtechitsolutions.strategy.CodaBeanIOStrategy;
import com.wtechitsolutions.strategy.CodaBindyStrategy;
import com.wtechitsolutions.strategy.CodaFixedFormat4JStrategy;
import com.wtechitsolutions.strategy.CodaFixedLengthStrategy;
import com.wtechitsolutions.strategy.FileGenerationStrategy;
import com.wtechitsolutions.strategy.SwiftBeanIOStrategy;
import com.wtechitsolutions.strategy.SwiftBindyStrategy;
import com.wtechitsolutions.strategy.SwiftFixedFormat4JStrategy;
import com.wtechitsolutions.strategy.SwiftFixedLengthStrategy;
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
import org.openjdk.jmh.annotations.TearDown;
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
 * JMH micro-benchmarks for all 8 FileGenerationStrategy implementations.
 * Measures throughput (records/second) and average time per operation.
 *
 * Run via: mvn test -Pbenchmark -Pskip-frontend
 *
 * Results are written to target/jmh-result.json and can be aggregated
 * using tools/python/benchmark_aggregator.py.
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

    // SWIFT strategies
    private FileGenerationStrategy swiftBeanIO;
    private FileGenerationStrategy swiftFixedFormat4J;
    private FileGenerationStrategy swiftFixedLength;
    private FileGenerationStrategy swiftBindy;

    @Setup
    public void setup() throws Exception {
        accounts = buildAccounts(5);
        transactions = buildTransactions(accounts, 20);

        BeanIOFormatter beanIO = new BeanIOFormatter();
        FixedFormat4JFormatter ff4j = new FixedFormat4JFormatter();
        FixedLengthFormatter vl = new FixedLengthFormatter();
        BindyFormatter bindy = new BindyFormatter();
        bindy.init();

        codaBeanIO = new CodaBeanIOStrategy(beanIO);
        codaFixedFormat4J = new CodaFixedFormat4JStrategy(ff4j);
        codaFixedLength = new CodaFixedLengthStrategy(vl);
        codaBindy = new CodaBindyStrategy(bindy);

        swiftBeanIO = new SwiftBeanIOStrategy(beanIO);
        swiftFixedFormat4J = new SwiftFixedFormat4JStrategy(ff4j);
        swiftFixedLength = new SwiftFixedLengthStrategy(vl);
        swiftBindy = new SwiftBindyStrategy(bindy);
    }

    @TearDown
    public void teardown() throws Exception {
        // BindyFormatter manages a CamelContext; no explicit shutdown needed in benchmark
    }

    @Benchmark public String codaBeanIO()        { return codaBeanIO.generate(transactions, accounts); }
    @Benchmark public String codaFixedFormat4J() { return codaFixedFormat4J.generate(transactions, accounts); }
    @Benchmark public String codaFixedLength()   { return codaFixedLength.generate(transactions, accounts); }
    @Benchmark public String codaBindy()         { return codaBindy.generate(transactions, accounts); }

    @Benchmark public String swiftBeanIO()        { return swiftBeanIO.generate(transactions, accounts); }
    @Benchmark public String swiftFixedFormat4J() { return swiftFixedFormat4J.generate(transactions, accounts); }
    @Benchmark public String swiftFixedLength()   { return swiftFixedLength.generate(transactions, accounts); }
    @Benchmark public String swiftBindy()         { return swiftBindy.generate(transactions, accounts); }

    /**
     * JUnit 5 entry point — invoked by `mvn test -Pbenchmark`.
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

    // ---- data builders ----

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
