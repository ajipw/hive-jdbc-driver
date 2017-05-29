package veil.hdp.hive.jdbc;

import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import veil.hdp.hive.jdbc.thrift.ThriftTransport;
import veil.hdp.hive.jdbc.utils.BinaryUtils;
import veil.hdp.hive.jdbc.utils.ZookeeperUtils;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class BinaryZookeeperHiveDriver extends HiveDriver {

    private static final Logger log = LoggerFactory.getLogger(BinaryZookeeperHiveDriver.class);

    static {
        try {

            DriverManager.registerDriver(new BinaryZookeeperHiveDriver());

            if (log.isInfoEnabled()) {
                log.info("driver [{}] has been registered.", BinaryZookeeperHiveDriver.class.getName());
            }

        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
    }


    @Override
    ThriftTransport buildTransport(Properties properties) throws SQLException {
        TTransport binaryTransport = BinaryUtils.createBinaryTransport(properties, getLoginTimeout());

        return ThriftTransport.builder().transport(binaryTransport).build();
    }


    @Override
    PropertiesCallback buildPropertiesCallback() {
        return (properties, uri) -> {

            HiveDriverProperty.TRANSPORT_MODE.set(properties, TransportMode.binary.name());
            HiveDriverProperty.ZOOKEEPER_DISCOVERY_ENABLED.set(properties, true);

            String authority = uri.getAuthority();

            ZookeeperUtils.loadPropertiesFromZookeeper(authority, properties);
        };
    }


}