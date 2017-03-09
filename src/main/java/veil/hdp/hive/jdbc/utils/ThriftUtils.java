package veil.hdp.hive.jdbc.utils;

import org.apache.hive.service.auth.HiveAuthFactory;
import org.apache.hive.service.auth.PlainSaslHelper;
import org.apache.hive.service.cli.thrift.TCLIService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import veil.hdp.hive.jdbc.HiveConfiguration;

import javax.security.sasl.SaslException;


public class ThriftUtils {

    private static final Logger log = LoggerFactory.getLogger(ThriftUtils.class);

    public static void openTransport(TTransport transport) throws TTransportException {

        if (!transport.isOpen()) {
            transport.open();
        }

    }

    public static void closeTransport(TTransport transport) {

        if (transport.isOpen()) {
            transport.close();
        }

    }

    public static TCLIService.Client createClient(TTransport transport) {
        return new TCLIService.Client(new TBinaryProtocol(transport));
    }


    public static TTransport createBinaryTransport(HiveConfiguration hiveConfiguration, int loginTimeoutMilliseconds) throws SaslException {

        if (hiveConfiguration.isNoSasl()) {
            return HiveAuthFactory.getSocketTransport(hiveConfiguration.getHost(), hiveConfiguration.getPort(), loginTimeoutMilliseconds);
        } else {
            //no support for delegation tokens or ssl yet

            TTransport socketTransport = HiveAuthFactory.getSocketTransport(hiveConfiguration.getHost(), hiveConfiguration.getPort(), loginTimeoutMilliseconds);

            //hack: password can't be empty.  must always specify a non-null, non-empty string
            return PlainSaslHelper.getPlainTransport(hiveConfiguration.getUser(), hiveConfiguration.getPassword(), socketTransport);

        }

    }


}
