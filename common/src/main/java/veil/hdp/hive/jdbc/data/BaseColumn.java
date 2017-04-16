package veil.hdp.hive.jdbc.data;

import org.apache.hive.service.cli.thrift.TColumn;
import org.slf4j.Logger;
import veil.hdp.hive.jdbc.HiveDriver;
import veil.hdp.hive.jdbc.metadata.ColumnDescriptor;
import veil.hdp.hive.jdbc.metadata.HiveType;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.BitSet;

import static org.slf4j.LoggerFactory.getLogger;
import static veil.hdp.hive.jdbc.metadata.HiveType.*;

public class BaseColumn<T> implements Column<T> {

    final Logger log = getLogger(getClass());

    final ColumnDescriptor descriptor;

    final T value;

    BaseColumn(ColumnDescriptor columnDescriptor, T value) {
        this.descriptor = columnDescriptor;
        this.value = value;
    }

    public ColumnDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public Boolean asBoolean() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asBoolean");
    }

    @Override
    public Date asDate() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asDate");
    }

    @Override
    public Timestamp asTimestamp() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asTimestamp");
    }

    @Override
    public BigDecimal asBigDecimal() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asBigDecimal");
    }

    @Override
    public Double asDouble() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asDouble");
    }

    @Override
    public Float asFloat() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asFloat");
    }

    @Override
    public Integer asInt() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asInt");
    }

    @Override
    public Long asLong() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asLong");
    }

    @Override
    public Short asShort() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asShort");
    }

    @Override
    public String asString() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asString");
    }

    @Override
    public Byte asByte() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asByte");
    }

    @Override
    public byte[] asByteArray() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asByteArray");
    }

    @Override
    public InputStream asInputStream() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asInputStream");
    }

    @Override
    public Time asTime() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asTime");
    }

    @Override
    public Object asObject() {
        return value;
    }

    @Override
    public Character asCharacter() throws SQLException {
        throw HiveDriver.notImplemented(this.getClass(), "asCharacter");
    }

    public static class Builder {

        private static final byte[] MASKS = new byte[]{
                0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, (byte) 0x80
        };
        private TColumn tColumn;
        private ColumnDescriptor descriptor;
        private int index;

        private static boolean isNull(int index, byte[] nulls) {
            int length = nulls.length;
            int bits = length * 8;
            BitSet bitset = new BitSet(bits);
            for (int i = 0; i < bits; i++) {
                int i1 = i / 8;
                byte aNull = nulls[i1];
                int i2 = i % 8;
                byte mask = MASKS[i2];
                boolean wtf = (aNull & mask) != 0;
                bitset.set(i, wtf);
            }

            return bitset.get(index);
        }

        public Builder column(TColumn tColumn) {
            this.tColumn = tColumn;
            return this;
        }


        public Builder descriptor(ColumnDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Column build() {

            HiveType hiveType = descriptor.getColumnType().getHiveType();

            if (tColumn.isSetBoolVal()) {

                Boolean value = tColumn.getBoolVal().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getBoolVal().getNulls());

                return new BooleanColumn(descriptor, isnull ? null : value);

            } else if (tColumn.isSetByteVal()) {

                Byte value = tColumn.getByteVal().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getByteVal().getNulls());

                return new ByteColumn(descriptor, isnull ? null : value);

            } else if (tColumn.isSetI16Val()) {

                Short value = tColumn.getI16Val().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getI16Val().getNulls());

                return new ShortColumn(descriptor, isnull ? null : value);

            } else if (tColumn.isSetI32Val()) {

                Integer value = tColumn.getI32Val().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getI32Val().getNulls());

                return new IntegerColumn(descriptor, isnull ? null : value);

            } else if (tColumn.isSetI64Val()) {

                Long value = tColumn.getI64Val().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getI64Val().getNulls());

                if (hiveType.equals(TIMESTAMP)) {
                    return new TimestampColumn(descriptor, isnull ? null : new Timestamp(value));
                } else if (hiveType.equals(DATE)) {
                    return new DateColumn(descriptor, isnull ? null : new Date(value));
                } else {
                    return new LongColumn(descriptor, isnull ? null : value);
                }


            } else if (tColumn.isSetDoubleVal()) {

                Double value = tColumn.getDoubleVal().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getDoubleVal().getNulls());

                if (hiveType.equals(FLOAT)) {
                    return new FloatColumn(descriptor, isnull ? null : new Float(value));
                } else {
                    return new DoubleColumn(descriptor, isnull ? null : value);
                }


            } else if (tColumn.isSetBinaryVal()) {

                ByteBuffer value = tColumn.getBinaryVal().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getBinaryVal().getNulls());

                return new BinaryColumn(descriptor, isnull ? null : value);

            } else if (tColumn.isSetStringVal()) {
                //may need to convert; STRING, LIST, MAP, STRUCT, UNIONTYPE, DECIMAL, NULL, CHAR, VARCHAR

                String value = tColumn.getStringVal().getValues().get(index);

                boolean isnull = isNull(index, tColumn.getStringVal().getNulls());

                if (hiveType.equals(DECIMAL)) {
                    return new DecimalColumn(descriptor, isnull ? null : new BigDecimal(value));
                } else if (hiveType.equals(CHAR)) {
                    return new CharacterColumn(descriptor, isnull ? null : value.charAt(0));
                } else if (hiveType.equals(VARCHAR)) {
                    return new VarcharColumn(descriptor, isnull ? null : value);
                } else {
                    return new StringColumn(descriptor, isnull ? null : value);
                }

                // todo; add others

            } else {
                throw new IllegalStateException(veil.hdp.hive.jdbc.utils.Utils.format("unknown column type for TColumn [{}]", tColumn));
            }


        }

    }
}
