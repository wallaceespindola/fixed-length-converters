package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BeanIoCodaRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import org.beanio.BeanReader;
import org.beanio.BeanWriter;
import org.beanio.StreamFactory;
import org.beanio.builder.Align;
import org.beanio.builder.FieldBuilder;
import org.beanio.builder.RecordBuilder;
import org.beanio.builder.StreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Formatter wrapping BeanIO (2.1.0) for CODA and SWIFT MT serialisation.
 * Uses programmatic FieldBuilder for CODA (fixed-length) with explicit 0-based positions.
 * SWIFT uses MT940 tag format (same as other strategies) via SwiftMtRecord.toSwiftFormat().
 * No XML mapping files required — fully annotation/programmatic-based.
 */
@Component
public class BeanIOFormatter {

    private static final Logger log = LoggerFactory.getLogger(BeanIOFormatter.class);

    private final StreamFactory factory;

    public BeanIOFormatter() {
        factory = StreamFactory.newInstance();

        // CODA: fixed-length stream — FieldBuilder.at() uses 0-based character positions
        factory.define(new StreamBuilder("coda")
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

        log.debug("BeanIO StreamFactory defined: coda(fixedlength/FieldBuilder)");
    }

    public String formatCoda(List<CodaRecord> records) {
        StringWriter sw = new StringWriter();
        BeanWriter writer = factory.createWriter("coda", sw);
        for (CodaRecord record : records) {
            writer.write("codaRecord", toBeanIo(record));
        }
        writer.flush();
        writer.close();
        return ensureCodaLineLength(sw.toString());
    }

    public List<CodaRecord> parseCoda(String content) {
        List<CodaRecord> result = new ArrayList<>();
        String padded = ensureCodaLineLength(content);
        BeanReader reader = factory.createReader("coda", new StringReader(padded));
        try {
            Object record;
            while ((record = reader.read()) != null) {
                if (record instanceof BeanIoCodaRecord bcr) {
                    result.add(fromBeanIo(bcr));
                }
            }
        } catch (Exception e) {
            log.warn("BeanIO CODA parse warning: {}", e.getMessage());
        } finally {
            reader.close();
        }
        return result;
    }

    public String formatSwift(List<SwiftMtRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (SwiftMtRecord record : records) {
            sb.append(record.toSwiftFormat());
            sb.append("---\n");
        }
        return sb.toString();
    }

    public List<SwiftMtRecord> parseSwift(String content) {
        List<SwiftMtRecord> result = new ArrayList<>();
        String[] sections = content.split("---\n");
        for (String section : sections) {
            if (section.isBlank()) continue;
            result.add(parseSwiftSection(section));
        }
        return result;
    }

    private SwiftMtRecord parseSwiftSection(String section) {
        SwiftMtRecord.SwiftMtRecordBuilder builder = SwiftMtRecord.builder();
        Arrays.stream(section.split("\n")).forEach(line -> {
            if (line.startsWith(":20:")) builder.transactionReference(line.substring(4).trim());
            else if (line.startsWith(":25:")) builder.accountIdentification(line.substring(4).trim());
            else if (line.startsWith(":28C:")) builder.statementNumber(line.substring(5).trim());
            else if (line.startsWith(":60F:")) builder.openingBalance(line.substring(5).trim());
            else if (line.startsWith(":61:")) {
                String entry = line.substring(4);
                if (entry.length() >= 10) {
                    builder.valueDate(entry.substring(0, 6));
                    builder.entryDate(entry.substring(6, 10));
                    builder.debitCreditMark(entry.length() > 10 ? String.valueOf(entry.charAt(10)) : "C");
                    int amtEnd = entry.indexOf("NMSC");
                    builder.amount(amtEnd > 11 ? entry.substring(11, amtEnd).trim() : "0,00");
                    builder.transactionType("NMSC");
                    builder.customerReference(amtEnd >= 0 && entry.length() > amtEnd + 4
                            ? entry.substring(amtEnd + 4).trim() : "NONREF");
                }
            } else if (line.startsWith(":86:")) builder.information(line.substring(4).trim());
            else if (line.startsWith(":62F:")) builder.closingBalance(line.substring(5).trim());
        });
        return builder.build();
    }

    private BeanIoCodaRecord toBeanIo(CodaRecord r) {
        BeanIoCodaRecord b = new BeanIoCodaRecord();
        b.setRecordType(r.getRecordType() != null ? r.getRecordType() : " ");
        b.setBankId(pad(r.getBankId(), 3));
        b.setReferenceNumber(pad(r.getReferenceNumber(), 10));
        b.setAccountNumber(pad(r.getAccountNumber(), 37));
        b.setCurrency(pad(r.getCurrency(), 3));
        b.setAmountStr(padAmount(r.getAmount(), 16));
        b.setEntryDate(pad(r.getEntryDate(), 6));
        b.setValueDate(pad(r.getValueDate(), 6));
        b.setDescription(pad(r.getDescription(), 32));
        b.setTransactionCode(pad(r.getTransactionCode(), 3));
        b.setSequenceNumber(padRight(r.getSequenceNumber(), 4));
        b.setFiller(pad(r.getFiller() != null ? r.getFiller() : "", 7));
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

    private String ensureCodaLineLength(String content) {
        if (content == null || content.isBlank()) return content;
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (line.isBlank()) continue;
            if (line.length() < 128) sb.append(line).append(" ".repeat(128 - line.length()));
            else sb.append(line, 0, 128);
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String pad(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return value + " ".repeat(length - value.length());
    }

    private static String padRight(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return " ".repeat(length - value.length()) + value;
    }

    private static String padAmount(BigDecimal amount, int length) {
        if (amount == null) amount = BigDecimal.ZERO;
        // Strip decimal places before encoding — amounts stored as plain integers in CODA
        String s = amount.abs().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        if (s.length() >= length) return s.substring(0, length);
        return "0".repeat(length - s.length()) + s;
    }

    private static BigDecimal parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
