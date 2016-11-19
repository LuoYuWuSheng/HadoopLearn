package site.luoyu;


import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.commons.logging.LogFactory;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tephra.TransactionContext;
import org.apache.tephra.TransactionFailureException;
import org.apache.tephra.distributed.TransactionServiceClient;
import org.apache.tephra.hbase.TransactionAwareHTable;
import org.apache.tephra.hbase.coprocessor.TransactionProcessor;
import org.apache.tephra.runtime.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;

/**
 * HBase CURD Demo
 * Configuration is under hadoop-common jar
 */
public class CURDdemo
{
    private Logger logger = LogManager.getLogger(CURDdemo.class);
    private Configuration configuration;
    private String tableName1 = "tephra1";
    private String tableName2 = "tephra2";
    private HTable hTable;
    private HTable hTable2;

    public Logger getLogger() {
        return logger;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public HTable gethTable() {
        return hTable;
    }

    public CURDdemo() {
        this.configuration = HBaseConfiguration.create();
        URL hdfsConfigURL = CURDdemo.class.getResource("/HDFSOperator.xml");
        URL hbaseConfigURL = CURDdemo.class.getResource("/HBaseOperator.xml");
        logger.info("hbase-site locationg:"+hbaseConfigURL);
        configuration.addResource(hbaseConfigURL);
        configuration.addResource(hdfsConfigURL);
        try {
            this.hTable = new HTable(configuration,tableName1);
            this.hTable2 = new HTable(configuration,tableName2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void supportTx(String tableName){
        try {
            HBaseAdmin hBaseAdmin = new HBaseAdmin(configuration);
            HTableDescriptor hTableDescriptor = hBaseAdmin.getTableDescriptor(Bytes.toBytes(tableName));
            hTableDescriptor.addCoprocessor(TransactionProcessor.class.getName());

            hBaseAdmin.disableTable(Bytes.toBytes(tableName));
            hBaseAdmin.modifyTable(Bytes.toBytes(tableName),hTableDescriptor);
            hBaseAdmin.enableTable(Bytes.toBytes(tableName));

        } catch (IOException e) {
            logger.info("尝试为旧表添加协处理器失败");
            e.printStackTrace();
        }
    }

    public void PutData()
    {
        Put put = new Put(Bytes.toBytes("T1Row1"));
        put.add(Bytes.toBytes("f1"),Bytes.toBytes("col1"),Bytes.toBytes("val1"));
        //table 2 doesn't have f2
        Put put2 = new Put(Bytes.toBytes("T2Row1"));
        put2.add(Bytes.toBytes("f1"),Bytes.toBytes("col1"),Bytes.toBytes("val1"));
        try {
            hTable.put(put);
            hTable2.put(put2);
        } catch (InterruptedIOException e) {
            e.printStackTrace();
        } catch (RetriesExhaustedWithDetailsException e) {
            e.printStackTrace();
        }
    }

    public void TephraDemo() throws IOException {

//        PooledClientProvider pooledClientProvider = new PooledClientProvider(configuration,new Discoveryservice)
//        TransactionServiceClient txClient = new TransactionServiceClient(configuration,)

        //google guice 哈哈，又能学新东西了。
        ConfigModule configModule = new ConfigModule(configuration);
        Injector injector = Guice.createInjector(
//                todo 很多配置问题不懂 configmodule 是加载配置的，discovery module需要zk
                configModule,
                new TransactionClientModule(),
//                不使用discoverymodule 将使用配置文件中配置的主机
                new DiscoveryModules().getDistributedModules(),
                new ZKModule(),
//                TransactionModule 没有什么用
                new TransactionModules().getDistributedModules()
        );
        TransactionServiceClient client = injector.getInstance(TransactionServiceClient.class);

        TransactionAwareHTable transactionAwareHTable1 = new TransactionAwareHTable(hTable);
        TransactionAwareHTable transactionAwareHTable2 = new TransactionAwareHTable(hTable2);
        TransactionContext context = new TransactionContext(client, transactionAwareHTable1,transactionAwareHTable2);

        Put put = new Put(Bytes.toBytes("T1Row3"));
        put.add(Bytes.toBytes("f1"),Bytes.toBytes("col1"),Bytes.toBytes("val1"));
        //table 2 doesn't have f2
        Put put2 = new Put(Bytes.toBytes("T2Row3"));
        put2.add(Bytes.toBytes("f2"),Bytes.toBytes("col1"),Bytes.toBytes("val1"));

        try {
            context.start();
            //tx
            transactionAwareHTable1.put(put);
            transactionAwareHTable2.put(put2);
            //tx
            context.finish();
        } catch (TransactionFailureException e) {
            try {
                //也抛出异常
                context.abort();
            } catch (TransactionFailureException e1) {
                logger.info("Tephra 回滚事务失败");
                e1.printStackTrace();
            }
        }
    }
}
