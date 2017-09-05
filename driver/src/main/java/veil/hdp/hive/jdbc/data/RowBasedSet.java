package veil.hdp.hive.jdbc.data;

import veil.hdp.hive.jdbc.Builder;

import java.util.ArrayList;
import java.util.List;

public class RowBasedSet {

    private final List<Row> rows;

    private RowBasedSet(List<Row> rows) {
        this.rows = rows;
    }

    public static RowBasedSetBuilder builder() {
        return new RowBasedSetBuilder();
    }

    public List<Row> getRows() {
        return rows;
    }

    public static class RowBasedSetBuilder implements Builder<RowBasedSet> {
        private ColumnBasedSet columnBasedSet;

        private RowBasedSetBuilder() {
        }

        public RowBasedSetBuilder columnBaseSet(ColumnBasedSet columnBasedSet) {
            this.columnBasedSet = columnBasedSet;
            return this;
        }

        public RowBasedSet build() {

            int totalRows = columnBasedSet.getRowCount();

            List<Row> rows = new ArrayList<>(totalRows);

            for (int r = 0; r < totalRows; r++) {
                rows.add(Row.builder().columnBasedSet(columnBasedSet).row(r).build());
            }

            return new RowBasedSet(rows);
        }
    }

}