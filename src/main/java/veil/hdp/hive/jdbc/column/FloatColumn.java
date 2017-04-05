package veil.hdp.hive.jdbc.column;

import veil.hdp.hive.jdbc.ColumnDescriptor;

import java.sql.SQLException;

/**
 * Created by tveil on 4/4/17.
 */
public class FloatColumn extends AbstractColumn<Float> {
    public FloatColumn(ColumnDescriptor descriptor, Float value) {
        super(descriptor, value);
    }

    @Override
    public Float getValue() {
        return value != null ? value : 0;
    }

    @Override
    public Float asFloat() throws SQLException {
        return getValue();
    }

    @Override
    public String asString() throws SQLException {
        return Float.toString(getValue());
    }

    @Override
    public Integer asInt() throws SQLException {
        log.warn("may lose precision going from {} to {}; value [{}]", Float.class, Integer.class, value);

        return getValue().intValue();

    }

    @Override
    public Long asLong() throws SQLException {
        log.warn("may lose precision going from {} to {}; value [{}]", Float.class, Long.class, value);

        return getValue().longValue();
    }

    @Override
    public Double asDouble() throws SQLException {
        log.warn("may lose precision going from {} to {}; value [{}]", Float.class, Double.class, value);

        return getValue().doubleValue();
    }

    @Override
    public Short asShort() throws SQLException {
        log.warn("may lose precision going from {} to {}; value [{}]", Float.class, Short.class, value);

        return getValue().shortValue();
    }


    @Override
    public Byte asByte() throws SQLException {
        log.warn("may lose precision going from {} to {}; value [{}]", Float.class, Byte.class, value);

        return getValue().byteValue();
    }
}