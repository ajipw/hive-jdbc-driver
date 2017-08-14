package veil.hdp.hive.jdbc.utils;

import com.google.common.collect.AbstractIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import veil.hdp.hive.jdbc.HiveDriverProperty;
import veil.hdp.hive.jdbc.HiveException;
import veil.hdp.hive.jdbc.bindings.TCLIService.Client;
import veil.hdp.hive.jdbc.bindings.*;
import veil.hdp.hive.jdbc.data.ColumnBasedSet;
import veil.hdp.hive.jdbc.data.Row;
import veil.hdp.hive.jdbc.data.RowBaseSet;
import veil.hdp.hive.jdbc.metadata.Schema;
import veil.hdp.hive.jdbc.thrift.*;

import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


public final class ThriftUtils {

    private static final Logger log = LoggerFactory.getLogger(ThriftUtils.class);

    private static final short FETCH_TYPE_QUERY = 0;
    private static final short FETCH_TYPE_LOG = 1;

    private ThriftUtils() {
    }

    public static void openTransport(TTransport transport, int timeout) {

        try {
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "open-thrift-transport-thread"));

            Future<Boolean> future = executor.submit(() -> {
                transport.open();

                return true;
            });

            future.get(timeout, TimeUnit.MILLISECONDS);

        } catch (InterruptedException | ExecutionException e) {
            throw new HiveException(e);
        } catch (TimeoutException e) {
            throw new HiveException("The Thrift Transport did not open prior to Timeout.  If using Kerberos, double check that you have a valid client Principal by running klist.", e);
        }

    }


    public static Client createClient(ThriftTransport transport) {
        return new Client(new TBinaryProtocol(transport.getTransport()));
    }


    public static TOpenSessionResp openSession(Properties properties, Client client, TProtocolVersion protocolVersion) throws InvalidProtocolException {


        TOpenSessionReq openSessionReq = new TOpenSessionReq(protocolVersion);

        // set properties for session
        Map<String, String> configuration = buildSessionConfig(properties);

        openSessionReq.setConfiguration(configuration);

        try {
            TOpenSessionResp resp = client.OpenSession(openSessionReq);

            checkStatus(resp.getStatus());

            return resp;
        } catch (TException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "'client_protocol' is unset")) {
                // this often happens when the protocol of the driver is higher than supported by the server.  in other words, driver is newer than server.  handle this gracefully.
                throw new InvalidProtocolException(e);
            } else {
                throw new HiveThriftException(e);
            }
        }

    }


    private static Map<String, String> buildSessionConfig(Properties properties) {
        Map<String, String> openSessionConfig = new HashMap<>();

        for (String property : properties.stringPropertyNames()) {
            // no longer going to use HiveConf.ConfVars to validate properties.  it requires too many dependencies.  let server side deal with this.
            if (property.startsWith("hive.")) {
                openSessionConfig.put("set:hiveconf:" + property, properties.getProperty(property));
            }
        }

        openSessionConfig.put("use:database", HiveDriverProperty.DATABASE_NAME.get(properties));

        return openSessionConfig;
    }

    public static void closeSession(ThriftSession thriftSession) {
        TCloseSessionReq closeRequest = new TCloseSessionReq(thriftSession.getSessionHandle());

        TCloseSessionResp resp = null;

        ReentrantLock lock = thriftSession.getSessionLock();
        Client client = thriftSession.getClient();

        lock.lock();

        try {
            resp = client.CloseSession(closeRequest);
        } catch (TTransportException e) {
            log.warn(MessageFormat.format("thrift transport exception: type [{0}]", e.getType()), e);
        } catch (TException e) {
            log.warn(MessageFormat.format("thrift exception exception: message [{0}]", e.getMessage()), e);
        } finally {
            lock.unlock();
        }

        if (resp != null) {
            try {
                checkStatus(resp.getStatus());
            } catch (HiveThriftException e) {
                log.warn(MessageFormat.format("sql exception: message [{0}]", e.getMessage()), e);
            }
        }

    }

    public static void closeOperation(ThriftOperation operation) {
        closeOperation(operation.getSession(), operation.getOperationHandle());
    }

    public static void closeOperation(ThriftSession session, TOperationHandle handle) {
        TCloseOperationReq closeRequest = new TCloseOperationReq(handle);

        TCloseOperationResp resp = null;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.CloseOperation(closeRequest);
        } catch (TTransportException e) {
            log.warn(MessageFormat.format("thrift transport exception: type [{0}]", e.getType()), e);
        } catch (TException e) {
            log.warn(MessageFormat.format("thrift exception exception: message [{0}]", e.getMessage()), e);
        } finally {
            lock.unlock();
        }

        if (resp != null) {
            try {
                checkStatus(resp.getStatus());
            } catch (HiveThriftException e) {
                log.warn(MessageFormat.format("sql exception: message [{0}]", e.getMessage()), e);
            }
        }

    }

    public static void cancelOperation(ThriftOperation operation) {
        TCancelOperationReq cancelRequest = new TCancelOperationReq(operation.getOperationHandle());

        TCancelOperationResp resp = null;

        ReentrantLock lock = operation.getSession().getSessionLock();
        Client client = operation.getSession().getClient();

        lock.lock();

        try {
            resp = client.CancelOperation(cancelRequest);
        } catch (TTransportException e) {
            log.warn(MessageFormat.format("thrift transport exception: type [{0}]", e.getType()), e);
        } catch (TException e) {
            log.warn(MessageFormat.format("thrift exception exception: message [{0}]", e.getMessage()), e);
        } finally {
            lock.unlock();
        }

        if (resp != null) {
            try {
                checkStatus(resp.getStatus());
            } catch (HiveThriftException e) {
                log.warn(MessageFormat.format("sql exception: message [{0}]", e.getMessage()), e);
            }
        }
    }

    static ThriftOperation getCatalogsOperation(ThriftSession session, int fetchSize) {
        TGetCatalogsReq req = new TGetCatalogsReq(session.getSessionHandle());

        TGetCatalogsResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetCatalogs(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();


    }

    static ThriftOperation getColumnsOperation(ThriftSession session, String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern, int fetchSize) {
        TGetColumnsReq req = new TGetColumnsReq(session.getSessionHandle());
        req.setCatalogName(catalog);
        req.setSchemaName(schemaPattern);
        req.setTableName(tableNamePattern == null ? "%" : tableNamePattern);
        req.setColumnName(columnNamePattern == null ? "%" : columnNamePattern);

        TGetColumnsResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetColumns(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();

    }

    static ThriftOperation getFunctionsOperation(ThriftSession session, String catalog, String schemaPattern, String functionNamePattern, int fetchSize) {
        TGetFunctionsReq req = new TGetFunctionsReq();
        req.setSessionHandle(session.getSessionHandle());
        req.setCatalogName(catalog);
        req.setSchemaName(schemaPattern);
        req.setFunctionName(functionNamePattern == null ? "%" : functionNamePattern);

        TGetFunctionsResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetFunctions(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();


    }

    static ThriftOperation getTablesOperation(ThriftSession session, String catalog, String schemaPattern, String tableNamePattern, String[] types, int fetchSize) {
        TGetTablesReq req = new TGetTablesReq(session.getSessionHandle());

        req.setCatalogName(catalog);
        req.setSchemaName(schemaPattern);
        req.setTableName(tableNamePattern == null ? "%" : tableNamePattern);

        if (types != null) {
            req.setTableTypes(Arrays.asList(types));
        }

        TGetTablesResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetTables(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();


    }

    static ThriftOperation getTypeInfoOperation(ThriftSession session, int fetchSize) {
        TGetTypeInfoReq req = new TGetTypeInfoReq(session.getSessionHandle());

        TGetTypeInfoResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetTypeInfo(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();


    }

    public static TGetInfoValue getServerInfo(ThriftSession session, TGetInfoType type) {
        TGetInfoReq req = new TGetInfoReq(session.getSessionHandle(), type);

        TGetInfoResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetInfo(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return resp.getInfoValue();

    }

    static ThriftOperation getTableTypesOperation(ThriftSession session, int fetchSize) {
        TGetTableTypesReq req = new TGetTableTypesReq(session.getSessionHandle());

        TGetTableTypesResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetTableTypes(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();

    }

    static ThriftOperation getDatabaseSchemaOperation(ThriftSession session, String catalog, String schemaPattern, int fetchSize) {
        TGetSchemasReq req = new TGetSchemasReq(session.getSessionHandle());
        req.setCatalogName(catalog);
        req.setSchemaName(schemaPattern);

        TGetSchemasResp resp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            resp = client.GetSchemas(req);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(resp.getStatus());

        return ThriftOperation.builder().handle(resp.getOperationHandle()).metaData(true).fetchSize(fetchSize).session(session).build();


    }

    private static void checkStatus(TStatus status) {

        if (status.getStatusCode() == TStatusCode.SUCCESS_STATUS || status.getStatusCode() == TStatusCode.SUCCESS_WITH_INFO_STATUS) {
            return;
        }

        throw new HiveThriftException(status);
    }

    private static TTableSchema getTableSchema(ThriftSession session, TOperationHandle handle) {
        TGetResultSetMetadataReq metadataReq = new TGetResultSetMetadataReq(handle);

        TGetResultSetMetadataResp metadataResp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            metadataResp = client.GetResultSetMetadata(metadataReq);
        } catch (TException e) {
            throw new HiveException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(metadataResp.getStatus());

        return metadataResp.getSchema();
    }

    private static TRowSet getRowSet(ThriftSession session, TFetchResultsReq tFetchResultsReq) {
        TFetchResultsResp fetchResults;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            fetchResults = client.FetchResults(tFetchResultsReq);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(fetchResults.getStatus());

        return fetchResults.getResults();
    }

    public static ThriftOperation executeSql(ThriftSession session, String sql, long queryTimeout, int fetchSize, int maxRows, int resultSetConcurrency, int resultSetHoldability, int resultSetType, int fetchDirection) {
        TExecuteStatementReq executeStatementReq = new TExecuteStatementReq(session.getSessionHandle(), StringUtils.trim(sql));
        executeStatementReq.setRunAsync(true);
        executeStatementReq.setQueryTimeout(queryTimeout);
        //todo: allows per statement configuration of session handle
        //executeStatementReq.setConfOverlay(null);

        TExecuteStatementResp executeStatementResp;

        ReentrantLock lock = session.getSessionLock();
        Client client = session.getClient();

        lock.lock();

        try {
            executeStatementResp = client.ExecuteStatement(executeStatementReq);
        } catch (TException e) {
            throw new HiveThriftException(e);
        } finally {
            lock.unlock();
        }

        checkStatus(executeStatementResp.getStatus());

        TOperationHandle operationHandle = executeStatementResp.getOperationHandle();

        if (HiveDriverProperty.FETCH_SERVER_LOGS.getBoolean(session.getProperties())) {

            ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "fetch-logs-thread"));

            executorService.submit(() -> {
                List<Row> rows = fetchLogs(session, operationHandle, Schema.builder().descriptors(StaticColumnDescriptors.QUERY_LOG).build(), fetchSize);

                for (Row row : rows) {
                    try {
                        log.debug(row.getColumn(1).asString());
                    } catch (SQLException e) {
                        log.error(e.getMessage(), e);
                    }
                }
            });
        }



        waitForStatementToComplete(session, operationHandle);

        return ThriftOperation.builder()
                .session(session)
                .handle(operationHandle)
                .resultSetConcurrency(resultSetConcurrency)
                .resultSetHoldability(resultSetHoldability)
                .maxRows(maxRows)
                .fetchSize(fetchSize)
                .fetchDirection(fetchDirection)
                .resultSetType(resultSetType)
                .build();

    }

    private static void waitForStatementToComplete(ThriftSession session, TOperationHandle handle) {
        boolean isComplete = false;

        TGetOperationStatusReq statusReq = new TGetOperationStatusReq(handle);

        while (!isComplete) {


            TGetOperationStatusResp statusResp;

            ReentrantLock lock = session.getSessionLock();
            Client client = session.getClient();

            lock.lock();

            try {
                statusResp = client.GetOperationStatus(statusReq);
            } catch (TException e) {
                throw new HiveThriftException(e);
            } finally {
                lock.unlock();
            }

            checkStatus(statusResp.getStatus());

            if (statusResp.isSetOperationState()) {

                switch (statusResp.getOperationState()) {
                    case FINISHED_STATE:
                        isComplete = true;
                        break;
                    case CLOSED_STATE:
                    case CANCELED_STATE:
                    case TIMEDOUT_STATE:
                    case ERROR_STATE:
                    case UKNOWN_STATE:
                        throw new HiveThriftException(statusResp);
                    case INITIALIZED_STATE:
                    case PENDING_STATE:
                    case RUNNING_STATE:
                        break;
                }
            }


        }
    }

    private static List<Row> fetchResults(ThriftSession session, TOperationHandle operationHandle, TFetchOrientation orientation, int fetchSize, Schema schema) {
        TFetchResultsReq fetchReq = new TFetchResultsReq(operationHandle, orientation, fetchSize);
        fetchReq.setFetchType(FETCH_TYPE_QUERY);

        return getRows(session, schema, fetchReq);
    }

    private static List<Row> fetchLogs(ThriftSession session, TOperationHandle operationHandle, Schema schema, int fetchSize) {

        TFetchResultsReq fetchReq = new TFetchResultsReq(operationHandle, TFetchOrientation.FETCH_FIRST, fetchSize);
        fetchReq.setFetchType(FETCH_TYPE_LOG);

        return getRows(session, schema, fetchReq);

    }

    private static List<Row> getRows(ThriftSession session, Schema schema, TFetchResultsReq tFetchResultsReq) {
        TRowSet results = getRowSet(session, tFetchResultsReq);

        return getRows(results, schema);


    }

    private static List<Row> getRows(TRowSet rowSet, Schema schema) {

        List<Row> rows = null;

        if (rowSet != null && rowSet.isSetColumns()) {

            ColumnBasedSet cbs = ColumnBasedSet.builder().rowSet(rowSet).schema(schema).build();

            rows = RowBaseSet.builder().columnBaseSet(cbs).build().getRows();

        }

        return rows;

    }

    public static Schema getSchema(ThriftSession session, TOperationHandle handle) {

        TTableSchema schema = getTableSchema(session, handle);

        return Schema.builder().schema(schema).build();


    }

    public static Iterable<Row> getResults(ThriftSession session, TOperationHandle handle, int fetchSize, Schema schema) {
        return () -> {

            Iterator<List<Row>> fetchIterator = fetchIterator(session, handle, fetchSize, schema);

            return new AbstractIterator<Row>() {

                private final AtomicInteger rowCount = new AtomicInteger(0);
                private Iterator<Row> rowSet;

                @Override
                protected Row computeNext() {
                    while (true) {
                        if (rowSet == null) {
                            if (fetchIterator.hasNext()) {
                                rowSet = fetchIterator.next().iterator();
                            } else {
                                return endOfData();
                            }
                        }

                        if (rowSet.hasNext()) {
                            // the page has more results
                            rowCount.incrementAndGet();
                            return rowSet.next();
                        } else if (rowCount.get() < fetchSize) {
                            // the page has no more results and the rowCount is < fetchSize; then i don't need
                            // to go back to the server to know if i'm done.
                            //
                            // for example rowCount = 10; fetchSize = 100; then no need to look for another page
                            //
                            return endOfData();
                        } else {
                            // the page has no more results, but rowCount = fetchSize.  need to check server for more results
                            rowSet = null;
                            rowCount.set(0);

                        }
                    }
                }
            };
        };
    }

    private static AbstractIterator<List<Row>> fetchIterator(ThriftSession session, TOperationHandle handle, int fetchSize, Schema schema) {
        return new AbstractIterator<List<Row>>() {

            @Override
            protected List<Row> computeNext() {

                List<Row> results = fetchResults(session, handle, TFetchOrientation.FETCH_NEXT, fetchSize, schema);

                if (results != null && !results.isEmpty()) {
                    return results;
                } else {
                    return endOfData();
                }

            }
        };
    }
}
