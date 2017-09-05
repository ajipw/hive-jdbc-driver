package veil.hdp.hive.jdbc.data;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import veil.hdp.hive.jdbc.Builder;

import java.util.ArrayList;
import java.util.List;

public class Row {

    private static final Logger log = LogManager.getLogger(Row.class);

    private final List<Column> columns;

    private Row(List<Column> columns) {
        this.columns = columns;
    }

    public static RowBuilder builder() {
        return new RowBuilder();
    }

    public Column getColumn(int position) {
        return columns.get(position - 1);
    }

    public static class RowBuilder implements Builder<Row> {

        private ColumnBasedSet columnBasedSet;
        private int row;

        private RowBuilder() {
        }

        public RowBuilder columnBasedSet(ColumnBasedSet columnBasedSet) {
            this.columnBasedSet = columnBasedSet;
            return this;
        }

        public RowBuilder row(int row) {
            this.row = row;
            return this;
        }


        public Row build() {


            int columnCount = columnBasedSet.getColumnCount();

            List<Column> columns = new ArrayList<>(columnCount);

            if (columnCount > 0) {

                for (ColumnData columnData : columnBasedSet.getColumns()) {
                    columns.add(BaseColumn.builder().row(row).columnData(columnData).build());
                }

            }

            return new Row(columns);

        }
    }
}
