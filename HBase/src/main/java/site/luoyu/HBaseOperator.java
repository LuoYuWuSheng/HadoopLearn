package site.luoyu;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.rest.protobuf.generated.ScannerMessage;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Computer user xd
 * Created by 张洋 on 2017/5/4.
 * Hbase 的操作类，用来与MeteData表交互
 */
public class HBaseOperator {
    private static final Log LOG = LogFactory.getLog(HBaseOperator.class);

    /**
     * 列族名，TileIndex（瓦片索引表）、MetaData（元数据表）中的列簇名都使用此列簇名
     */
    public static final String FAMILY_NAME_STRING = "imageFamily";

    /**
     * 列族名的byte[]数组形式
     */
    public static final byte[] FAMILY_NAME_BYTES;

    /**
     * HTablePool有一个maxsize，HTablePool针对每个表都有一个Pool，maxsize表示这个Pool的最大大小
     */
    public static final String HTABLE_POOL_MAX_SIZE_KEY = "htable.pool.max.size";
    public static final int HTABLE_POOL_MAX_SIZE_DEFAULT = 10;
    public static final int HTABLE_POOL_MAX_SIZE;

    /**
     * 是否使用HBase表对象连接池 HTablePool
     */
    public static final String USE_HTABLE_POOL_KEY = "use.htable.pool";
    public static final boolean USE_HTABLE_POOL_DEFAULT = false;
    public static final boolean USE_HTABLE_POOL;

    /**
     * 配置对象
     * 一. HTable对象共享Configuration对象，这样的好处在于：
     * 1.共享ZooKeeper的连接：每个客户端需要与ZooKeeper建立连接，查询用户的table
     * regions位置，这些信息可以在连接建立后缓存起来共享使用；
     * 2.共享公共的资源：客户端需要通过ZooKeeper查找-ROOT-和.META.表，这个需要网络传输开销，
     * 客户端缓存这些公共资源后能够减少后续的网络传输开销，加快查找过程速度。 因此，与以下这种方式相比：
     * <p>
     * HTable table1 = new HTable("table1");
     * HTable table2 = new HTable("table2");
     * 下面的方式更有效些：
     * <p>
     * Configuration conf = HBaseConfiguration.create();
     * HTable table1 = new HTable(conf, "table1");
     * HTable table2 = new HTable(conf, "table2");
     * <p>
     * <p>
     * 二. HTablePool可以解决HTable存在的线程不安全问题，同时通过维护固定数量的HTable对象，
     * 能够在程序运行期间复用这些HTable资源对象。
     * Configuration conf = HBaseConfiguration.create();
     * HTablePool pool = new HTablePool(conf, 10);
     * <p>
     * 1. HTablePool可以自动创建HTable对象，而且对客户端来说使用上是完全透明的，可以避免多线程间数据并发修改问题。
     * 2. HTablePool中的HTable对象之间是公用Configuration连接的，能够可以减少网络开销。
     * HTablePool的使用很简单：每次进行操作前，通过HTablePool的getTable方法取得一个HTable对象，然后进行put/get/
     * scan/delete等操作，最后通过HTablePool的putTable方法将HTable对象放回到HTablePool中。
     */
    private static Configuration conf;

    /**
     * HBaseAdmin管理对象
     */
    private static HBaseAdmin admin = null;

    /**
     * HTable 连接池对象
     */
    private static HConnection hTablePool = null;
    // private static HTablePool hTablePool = null;

    /**
     * 引用计数锁
     */
    private static Object lock = new Object();

    /**
     * HBaseAdmin管理对象引用计数
     */
    private static int refCount = 0;

    /**
     * 操作表对象实例
     */
    private HTableInterface table;

    static {
        // 初始化conf和从conf中获取配置时耗时
        FAMILY_NAME_BYTES = FAMILY_NAME_STRING.getBytes();

        conf = HBaseConfiguration.create();
        ArrayList<String> confFileList = new ArrayList<String>();
        URL hdfsConfigURL = CURDdemo.class.getResource("/HDFSOperator.xml");
        URL hbaseConfigURL = CURDdemo.class.getResource("/HBaseOperator.xml");
        conf.addResource(hdfsConfigURL);
        conf.addResource(hbaseConfigURL);
//        这个方法能让用户从配置文件中读取值?
        USE_HTABLE_POOL = conf.getBoolean(USE_HTABLE_POOL_KEY, USE_HTABLE_POOL_DEFAULT);
        HTABLE_POOL_MAX_SIZE = conf.getInt(HTABLE_POOL_MAX_SIZE_KEY, HTABLE_POOL_MAX_SIZE_DEFAULT);
    }

    /**
     * 构造函数，实际为根据配置对象conf创建HBaseAdmin对象和连接池对象
     */
    public HBaseOperator() throws IOException {
        synchronized (lock) {
            refCount++;
            if (USE_HTABLE_POOL) {
                if (hTablePool == null) {
                    // hTablePool = new HTablePool(conf, HTABLE_POOL_MAX_SIZE);
                    hTablePool = HConnectionManager.createConnection(conf);
                    LOG.info("与HBase的表连接使用HTablePool表连接池.");
                }
            }
            if (admin == null) {
                admin = new HBaseAdmin(conf);
                LOG.info("创建HBaseAdmin管理对象");
                table = new HTable(conf,"MetaData");
            }
        }
    }

    public ResultScanner getScanner(Scan scanner) throws IOException {
        return table.getScanner(scanner);
    }
}
