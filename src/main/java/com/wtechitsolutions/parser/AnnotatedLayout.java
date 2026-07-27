package com.wtechitsolutions.parser;

import com.ancientprogramming.fixedformat4j.annotation.Align;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives a fixed-length column layout from the field annotations of an already-annotated
 * model class, instead of restating offsets and widths as hand-written
 * {@link Range} literals.
 *
 * <p>Two annotation dialects are supported:</p>
 * <ul>
 *   <li>fixedformat4j — {@link com.ancientprogramming.fixedformat4j.annotation.Field}
 *       (offset, length, align, paddingChar)</li>
 *   <li>vitaliy fixedlength — {@link name.velikodniy.vitaliy.fixedlength.annotation.FixedField}
 *       (offset, length, align, padding)</li>
 * </ul>
 *
 * <p>The resulting {@link Column} list feeds both Spring Batch flat-file components:
 * {@link #tokenizer()} for the read path and {@link #formatString()} for the
 * {@link org.springframework.batch.item.file.transform.FormatterLineAggregator} write path.
 * A layout declared once on the model therefore drives serialisation, parsing and column
 * slicing — a single source of truth that cannot drift out of sync.</p>
 *
 * @author Wallace Espindola, wallace.espindola@gmail.com
 */
public final class AnnotatedLayout {

    /**
     * One fixed-length column.
     *
     * @param name       Java field name; doubles as the Spring Batch FieldSet key
     * @param offset     1-based start position
     * @param length     column width in characters
     * @param rightAlign {@code true} when values are right-aligned within the column
     * @param padChar    character used to fill the unused part of the column
     */
    public record Column(String name, int offset, int length, boolean rightAlign, char padChar) {

        /** 1-based inclusive end position of this column. */
        public int end() {
            return offset + length - 1;
        }
    }

    private final List<Column> columns;

    private AnnotatedLayout(List<Column> columns) {
        this.columns = List.copyOf(columns);
    }

    /** Reads the layout from {@code @Record}/{@code @Field} (fixedformat4j) annotations. */
    public static AnnotatedLayout fromFixedFormat4j(Class<?> modelType) {
        List<Column> cols = new ArrayList<>();
        for (Field f : modelType.getDeclaredFields()) {
            com.ancientprogramming.fixedformat4j.annotation.Field a =
                    f.getAnnotation(com.ancientprogramming.fixedformat4j.annotation.Field.class);
            if (a == null) continue;
            cols.add(new Column(f.getName(), a.offset(), a.length(), a.align() == Align.RIGHT, a.paddingChar()));
        }
        return build(modelType, cols);
    }

    /** Reads the layout from {@code @FixedLine}/{@code @FixedField} (vitaliy fixedlength) annotations. */
    public static AnnotatedLayout fromFixedLength(Class<?> modelType) {
        List<Column> cols = new ArrayList<>();
        for (Field f : modelType.getDeclaredFields()) {
            name.velikodniy.vitaliy.fixedlength.annotation.FixedField a =
                    f.getAnnotation(name.velikodniy.vitaliy.fixedlength.annotation.FixedField.class);
            if (a == null) continue;
            boolean right = a.align() == name.velikodniy.vitaliy.fixedlength.Align.RIGHT;
            cols.add(new Column(f.getName(), a.offset(), a.length(), right, a.padding()));
        }
        return build(modelType, cols);
    }

    private static AnnotatedLayout build(Class<?> modelType, List<Column> cols) {
        if (cols.isEmpty()) {
            throw new IllegalArgumentException("No fixed-length field annotations found on " + modelType.getName());
        }
        cols.sort(Comparator.comparingInt(Column::offset));
        int expected = 1;
        for (Column c : cols) {
            if (c.offset() != expected) {
                throw new IllegalArgumentException(String.format(
                        "Layout gap/overlap on %s at field '%s': expected offset %d but annotation declares %d",
                        modelType.getSimpleName(), c.name(), expected, c.offset()));
            }
            expected = c.end() + 1;
        }
        return new AnnotatedLayout(cols);
    }

    public List<Column> columns() {
        return columns;
    }

    /** Total record width in characters (sum of all column lengths). */
    public int recordLength() {
        return columns.stream().mapToInt(Column::length).sum();
    }

    /** Spring Batch tokenizer whose {@link Range} columns come straight from the annotations. */
    public FixedLengthTokenizer tokenizer() {
        FixedLengthTokenizer t = new FixedLengthTokenizer();
        t.setColumns(columns.stream().map(c -> new Range(c.offset(), c.end())).toArray(Range[]::new));
        t.setNames(columns.stream().map(Column::name).toArray(String[]::new));
        t.setStrict(false);
        return t;
    }

    /**
     * printf format string for {@link org.springframework.batch.item.file.transform.FormatterLineAggregator},
     * one conversion per column, widths summing to {@link #recordLength()}.
     * Right-aligned columns use {@code %N.Ns}, left-aligned use {@code %-N.Ns}.
     */
    public String formatString() {
        StringBuilder sb = new StringBuilder();
        for (Column c : columns) {
            sb.append('%').append(c.rightAlign() ? "" : "-")
                    .append(c.length()).append('.').append(c.length()).append('s');
        }
        return sb.toString();
    }
}
