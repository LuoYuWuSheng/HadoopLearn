package site.luoyu;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.KeyOnlyFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

/**
 * Computer user xd
 * Created by 张洋 on 2017/5/3.
 * 从实验室的云上读取瓦片数据的元数据。
 */
public class ClouodgisMeta {
    private HBaseAdmin hBaseAdmin;
    private static Logger logger = LogManager.getLogger(ClouodgisMeta.class);

    public ClouodgisMeta() {
        Configuration configuration = HBaseConfiguration.create();
        URL hdfsConfigURL = CURDdemo.class.getResource("/HDFSOperator.xml");
        URL hbaseConfigURL = CURDdemo.class.getResource("/HBaseOperator.xml");
        configuration.addResource(hdfsConfigURL);
        configuration.addResource(hbaseConfigURL);
        try {
            hBaseAdmin = new HBaseAdmin(configuration);
        } catch (IOException e) {
            logger.error("无法加载Hbase 与 Hdfs配置文件");
            e.printStackTrace();
        }
    }

    public ClouodgisMeta(Configuration configuration) throws IOException {
        this.hBaseAdmin = new HBaseAdmin(configuration);
    }

    public String getMeta() throws IOException {
        HBaseOperator hBaseOperator = new HBaseOperator();
//        keyOnlyFilter做什么的?
        Filter filter = new KeyOnlyFilter();
        ResultScanner results = null;
        Scan scan = new Scan();
        scan.setFilter(filter);
        results = hBaseOperator.getScanner(scan);
        for (Result res : results) {
            System.out.println(res.getColumnCells("MetaData".getBytes(), Bytes.toBytes("name")));
            System.out.println(Arrays.toString(res.getValue(Bytes.toBytes("MetaData"), Bytes.toBytes("name"))));
        }
        return null;
    }
}
