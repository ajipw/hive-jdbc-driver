package veil.hdp.hive.jdbc.data;

import veil.hdp.hive.jdbc.metadata.ColumnDescriptor;

import java.util.BitSet;
import java.util.List;

public abstract class AbstractColumnData<T> implements ColumnData {

    private static final byte[] MASKS = {
            0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, (byte) 0x80
    };


    private final ColumnDescriptor descriptor;
    private final List<T> values;
    private final BitSet nulls;
    private final int rowCount;

    AbstractColumnData(ColumnDescriptor descriptor, List<T> values, BitSet nulls, int rowCount) {
        this.descriptor = descriptor;
        this.values = values;
        this.nulls = nulls;
        this.rowCount = rowCount;
    }

    ColumnDescriptor getDescriptor() {
        return descriptor;
    }

    T getValue(int row) {
        if (isNull(row)) {
            return null;
        }

        return values.get(row);
    }

    private boolean isNull(int row) {
        return nulls.get(row);
    }

    @Override
    public int getRowCount() {
        return rowCount;
    }

}