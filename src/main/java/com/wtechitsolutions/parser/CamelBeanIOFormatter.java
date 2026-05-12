package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BeanIoCodaRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.dataformat.beanio.BeanIODataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.beanio.StreamFactory;
import org.beanio.builder.Align;
import org.beanio.builder.FieldBuilder;
import org.beanio.builder.RecordBuilder;
import org.beanio.builder.StreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatter wrapping Apache Camel BeanIO data format (4.20.0) for CODA serialisation.
 * Injects a pre-configured BeanIO {@link StreamFactory} (programmatic builder — no XML)
 * directly into {@link BeanIODataFormat#setFactory}, routing marshal/unmarshal calls
 * through a standalone {@link CamelContext}.
 *
 * The stream definition mirrors {@code BeanIOFormatter} exactly (same 0-based field
 * positions, same padding/alignment), ensuring output is identical to the BEANIO strategy.
 *
 * SWIFT delegates to {@link SwiftMtRecord#toSwiftFormat()} / {@link SwiftMtRecord#fromSwiftSection(String)}
 * — the same approach used by every other SWIFT strategy in the codebase.
 */
@Component
public class CamelBeanIOFormatter {

    private static final Logger log = LoggerFactory.getLogger(CamelBeanIOFormatter.class);
    private static final String STREAM = "coda";
    /** Classpath URI used to bootstrap BeanIODataFormat's internal init check; factory is replaced after start(). */
    private static final String MAPPING_CLASSPATH = "classpath:beanio/coda-mapping.xml";

    private CamelContext camelContext;
    private BeanIODataFormat codaFormat;

    @PostConstruct
    public void init() {
        try {
            camelContext = new DefaultCamelContext();
            // Use the mapping XML as bootstrap path to satisfy ResourceHelper.isClasspathUri()
            // check in BeanIODataFormat.doInit(); we then replace the factory with the
            // programmatically-built one after start() to avoid the XML annotation conflict.
            codaFormat = new BeanIODataFormat(MAPPING_CLASSPATH, STREAM);
            codaFormat.setCamelContext(camelContext);
            camelContext.start();
            codaFormat.start();
            // Replace with programmatic factory — ensures exact field layout defined in
            // buildStreamFactory() (mirrors BeanIOFormatter) is used for marshal/unmarshal.
            codaFormat.setFactory(buildStreamFactory());
            log.info("CamelBeanIOFormatter initialised with programmatic stream='{}'", STREAM);
        } catch (Exception e) {
            log.error("Failed to initialise CamelBeanIOFormatter: {}", e.getMessage());
            throw new RuntimeException("Camel BeanIO initialisation failed", e);
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            if (codaFormat != null) codaFormat.stop();
            if (camelContext != null) camelContext.stop();
        } catch (Exception e) {
            log.warn("CamelBeanIOFormatter shutdown error: {}", e.getMessage());
        }
    }

    public String formatCoda(List<CodaRecord> records) {
        try {
            List<BeanIoCodaRecord> beanioRecords = records.stream()
                    .map(this::toBeanIo)
                    .collect(Collectors.toList());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Exchange exchange = new DefaultExchange(camelContext);
            codaFormat.marshal(exchange, beanioRecords, out);
            return out.toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n");
        } catch (Exception e) {
            log.warn("Camel BeanIO CODA format failed, falling back to toFixedWidth: {}", e.getMessage());
            return records.stream()
                    .map(CodaRecord::toFixedWidth)
                    .collect(Collectors.joining("\n")) + "\n";
        }
    }

    @SuppressWarnings("unchecked")
    public List<CodaRecord> parseCoda(String content) {
        if (content == null || content.isBlank()) return List.of();
        try {
            String padded = Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(this::ensureWidth)
                    .collect(Collectors.joining("\n")) + "\n";
            Exchange exchange = new DefaultExchange(camelContext);
            Object parsed = codaFormat.unmarshal(exchange,
                    new ByteArrayInputStream(padded.getBytes(StandardCharsets.UTF_8)));
            List<CodaRecord> result = new ArrayList<>();
            if (parsed instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BeanIoCodaRecord bcr) result.add(fromBeanIo(bcr));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Camel BeanIO CODA parse failed, falling back to fromFixedWidth: {}", e.getMessage());
            return Arrays.stream(content.split("\n"))
                    .filter(l -> !l.isBlank())
                    .map(CodaRecord::fromFixedWidth)
                    .collect(Collectors.toList());
        }
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord r : records) sb.append(r.toSwiftFormat()).append("---\n");
        return sb.toString();
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        return Arrays.stream(content.split("---\n"))
                .filter(s -> !s.isBlank())
                .map(SwiftMtRecord::fromSwiftSection)
                .toList();
    }

    // ── stream factory ──────────────────────────────────────────────────────────

    /**
     * Builds the BeanIO stream factory programmatically, mirroring BeanIOFormatter exactly.
     * FieldBuilder.at() uses 0-based character positions; total record length = 128.
     */
    private static StreamFactory buildStreamFactory() {
        StreamFactory factory = StreamFactory.newInstance();
        factory.define(new StreamBuilder(STREAM)
                .format("fixedlength")
                .addRecord(new RecordBuilder("codaRecord")
                        .type(BeanIoCodaRecord.class)
                        .addField(new FieldBuilder("recordType").at(0).length(1).trim())
                        .addField(new FieldBuilder("bankId").at(1).length(3).trim())
                        .addField(new FieldBuilder("referenceNumber").at(4).length(10).trim())
                        .addField(new FieldBuilder("accountNumber").at(14).length(37).trim())
                        .addField(new FieldBuilder("currency").at(51).length(3).trim())
                        .addField(new FieldBuilder("amountStr").at(54).length(16)
                                .padding('0').align(Align.RIGHT).trim())
                        .addField(new FieldBuilder("entryDate").at(70).length(6).trim())
                        .addField(new FieldBuilder("valueDate").at(76).length(6).trim())
                        .addField(new FieldBuilder("description").at(82).length(32).trim())
                        .addField(new FieldBuilder("transactionCode").at(114).length(3).trim())
                        .addField(new FieldBuilder("sequenceNumber").at(117).length(4)
                                .align(Align.RIGHT).trim())
                        .addField(new FieldBuilder("filler").at(121).length(7).trim())));
        return factory;
    }

    // ── conversion helpers: raw values only; stream builder config handles all padding ──

    private BeanIoCodaRecord toBeanIo(CodaRecord r) {
        BeanIoCodaRecord b = new BeanIoCodaRecord();
        b.setRecordType(orEmpty(r.getRecordType()));
        b.setBankId(orEmpty(r.getBankId()));
        b.setReferenceNumber(orEmpty(r.getReferenceNumber()));
        b.setAccountNumber(orEmpty(r.getAccountNumber()));
        b.setCurrency(orEmpty(r.getCurrency()));
        b.setAmountStr(amountToStr(r.getAmount()));
        b.setEntryDate(orEmpty(r.getEntryDate()));
        b.setValueDate(orEmpty(r.getValueDate()));
        b.setDescription(orEmpty(r.getDescription()));
        b.setTransactionCode(orEmpty(r.getTransactionCode()));
        b.setSequenceNumber(orEmpty(r.getSequenceNumber()));
        b.setFiller(orEmpty(r.getFiller()));
        return b;
    }

    private CodaRecord fromBeanIo(BeanIoCodaRecord b) {
        return CodaRecord.builder()
                .recordType(trim(b.getRecordType()))
                .bankId(trim(b.getBankId()))
                .referenceNumber(trim(b.getReferenceNumber()))
                .accountNumber(trim(b.getAccountNumber()))
                .currency(trim(b.getCurrency()))
                .amount(parseAmount(b.getAmountStr()))
                .entryDate(trim(b.getEntryDate()))
                .valueDate(trim(b.getValueDate()))
                .description(trim(b.getDescription()))
                .transactionCode(trim(b.getTransactionCode()))
                .sequenceNumber(trim(b.getSequenceNumber()))
                .filler(trim(b.getFiller()))
                .build();
    }

    private String ensureWidth(String line) {
        if (line.length() < 128) return line + " ".repeat(128 - line.length());
        return line.length() > 128 ? line.substring(0, 128) : line;
    }

    private static String orEmpty(String s) { return s != null ? s : ""; }

    private static String trim(String s) { return s != null ? s.trim() : ""; }

    private static String amountToStr(BigDecimal a) {
        return (a != null ? a : BigDecimal.ZERO).abs().toBigInteger().toString();
    }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
