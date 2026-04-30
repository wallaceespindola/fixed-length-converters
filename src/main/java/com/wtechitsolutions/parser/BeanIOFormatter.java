package com.wtechitsolutions.parser;

import com.wtechitsolutions.parser.model.BeanIoCodaRecord;
import com.wtechitsolutions.parser.model.BeanIoSwiftRecord;
import com.wtechitsolutions.parser.model.CodaRecord;
import com.wtechitsolutions.parser.model.SwiftMtRecord;
import lombok.extern.slf4j.Slf4j;
import org.beanio.BeanReader;
import org.beanio.BeanWriter;
import org.beanio.StreamFactory;
import org.beanio.builder.RecordBuilder;
import org.beanio.builder.StreamBuilder;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Formatter wrapping BeanIO (2.1.0) for CODA and SWIFT MT serialisation.
 * Uses annotation-based mapping via @Record and @Field — no XML files required.
 * CODA: fixed-length stream (128 chars per record).
 * SWIFT: CSV stream (one row per transaction entry).
 */
@Component
@Slf4j
public class BeanIOFormatter {

    private final StreamFactory factory;

    public BeanIOFormatter() {
        factory = StreamFactory.newInstance();

        // CODA: annotation-driven fixed-length stream
        factory.define(new StreamBuilder("coda")
                .format("fixedlength")
                .addRecord(new RecordBuilder("codaRecord")
                        .type(BeanIoCodaRecord.class)
                        .minOccurs(0)
                        .maxOccurs(-1)));

        // SWIFT: annotation-driven CSV stream (tag-based content stored as CSV fields)
        factory.define(new StreamBuilder("swift")
                .format("csv")
                .addRecord(new RecordBuilder("swiftRecord")
                        .type(BeanIoSwiftRecord.class)
                        .minOccurs(0)
                        .maxOccurs(-1)));

        log.debug("BeanIO StreamFactory defined with annotation-based coda and swift streams");
    }

    /**
     * Serialises a list of CodaRecord objects to a fixed-length CODA string.
     */
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

    /**
     * Parses a fixed-length CODA string back into a list of CodaRecord objects.
     */
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

    /**
     * Serialises a list of SwiftMtRecord objects to CSV format for BeanIO.
     */
    public String formatSwift(List<SwiftMtRecord> records) {
        StringWriter sw = new StringWriter();
        BeanWriter writer = factory.createWriter("swift", sw);
        for (SwiftMtRecord record : records) {
            writer.write("swiftRecord", toBeanIoSwift(record));
        }
        writer.flush();
        writer.close();
        return sw.toString();
    }

    /**
     * Parses the BeanIO CSV-serialised SWIFT content back into SwiftMtRecord objects.
     */
    public List<SwiftMtRecord> parseSwift(String content) {
        List<SwiftMtRecord> result = new ArrayList<>();
        BeanReader reader = factory.createReader("swift", new StringReader(content));
        try {
            Object record;
            while ((record = reader.read()) != null) {
                if (record instanceof BeanIoSwiftRecord bsr) {
                    result.add(fromBeanIoSwift(bsr));
                }
            }
        } catch (Exception e) {
            log.warn("BeanIO SWIFT parse warning: {}", e.getMessage());
        } finally {
            reader.close();
        }
        return result;
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

    private BeanIoSwiftRecord toBeanIoSwift(SwiftMtRecord r) {
        BeanIoSwiftRecord b = new BeanIoSwiftRecord();
        b.setTransactionReference(r.getTransactionReference());
        b.setAccountIdentification(r.getAccountIdentification());
        b.setStatementNumber(r.getStatementNumber());
        b.setOpeningBalance(r.getOpeningBalance());
        b.setValueDate(r.getValueDate());
        b.setEntryDate(r.getEntryDate());
        b.setDebitCreditMark(r.getDebitCreditMark());
        b.setAmount(r.getAmount());
        b.setTransactionType(r.getTransactionType());
        b.setCustomerReference(r.getCustomerReference());
        b.setInformation(r.getInformation());
        b.setClosingBalance(r.getClosingBalance());
        return b;
    }

    private SwiftMtRecord fromBeanIoSwift(BeanIoSwiftRecord b) {
        return SwiftMtRecord.builder()
                .transactionReference(b.getTransactionReference())
                .accountIdentification(b.getAccountIdentification())
                .statementNumber(b.getStatementNumber())
                .openingBalance(b.getOpeningBalance())
                .valueDate(b.getValueDate())
                .entryDate(b.getEntryDate())
                .debitCreditMark(b.getDebitCreditMark())
                .amount(b.getAmount())
                .transactionType(b.getTransactionType())
                .customerReference(b.getCustomerReference())
                .information(b.getInformation())
                .closingBalance(b.getClosingBalance())
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
        String s = amount.abs().toPlainString().replace(".", "");
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
