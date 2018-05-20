package veil.hdp.hive.jdbc.thrift;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.transport.TTransport;
import veil.hdp.hive.jdbc.Builder;
import veil.hdp.hive.jdbc.HiveDriverProperty;
import veil.hdp.hive.jdbc.HiveException;
import veil.hdp.hive.jdbc.TransportMode;
import veil.hdp.hive.jdbc.utils.BinaryUtils;
import veil.hdp.hive.jdbc.utils.HttpUtils;
import veil.hdp.hive.jdbc.utils.ThriftUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThriftTransport implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(ThriftTransport.class);

    // constructor
    private TTransport transport;
    private List<Closeable> closeableList;

    // atomic
    private final AtomicBoolean closed = new AtomicBoolean(true);

    private ThriftTransport(TTransport transport, List<Closeable> closeableList) {
        this.transport = transport;
        this.closeableList = closeableList;

        closed.set(false);
    }

    public static ThriftTransportBuilder builder() {
        return new ThriftTransportBuilder();
    }

    public TTransport getTransport() {
        return transport;
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Determines if the ThriftTransport is in a valid state to execute another Thrift call. It checks both the closed flag as well as the underlying thrift transport status.
     *
     * @return true if valid, false if not valid
     */

    public boolean isValid() {
        return !closed.get() && transport.isOpen();
    }

    @Override
    public void close() {

        if (closed.compareAndSet(false, true)) {

            log.trace("attempting to close {}", this.getClass().getName());

            for (Closeable closeable : closeableList) {

                log.trace("attempting to close {}", closeable.getClass().getName());

                try {
                    closeable.close();
                } catch (IOException e) {
                    log.warn(e.getMessage(), e);
                }
            }

            closeableList = null;

            try {
                transport.close();
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            } finally {
                transport = null;
            }

        }
    }

    public static class ThriftTransportBuilder implements Builder<ThriftTransport> {


        private Properties properties;

        private ThriftTransportBuilder() {
        }

        public ThriftTransportBuilder properties(Properties properties) {
            this.properties = properties;
            return this;
        }

        public ThriftTransport build() {

            TransportMode mode = TransportMode.valueOf(HiveDriverProperty.TRANSPORT_MODE.get(properties));

            TTransport transport = null;

            List<Closeable> closeableList = new ArrayList<>(1);

            if (mode == TransportMode.binary) {
                transport = BinaryUtils.createBinaryTransport(properties);
            } else if (mode == TransportMode.http) {
                CloseableHttpClient client = HttpUtils.buildClient(properties);

                closeableList.add(client);

                transport = HttpUtils.createHttpTransport(properties, client);
            }

            if (transport == null) {
                throw new HiveException("invalid transport mode [" + mode + ']');
            }

            ThriftUtils.openTransport(transport, HiveDriverProperty.THRIFT_TRANSPORT_TIMEOUT.getInt(properties));

            return new ThriftTransport(transport, closeableList);
        }


    }
}
