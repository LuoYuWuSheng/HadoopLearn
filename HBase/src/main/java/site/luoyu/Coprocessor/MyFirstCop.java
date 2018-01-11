package site.luoyu.Coprocessor;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Computer user xd
 * Created by 张洋 on 2017/2/16.
 * Observer 类型的协处理器DEMO
 */
public class MyFirstCop extends BaseRegionObserver{
    private static Logger logger = LoggerFactory.getLogger(MyFirstCop.class);
    public static String version = "1";
    static {
        String os = System.getProperty("os.name");
        if(os.toLowerCase().startsWith("win")){
            logger.error("System is windows ");
        }else {
            logger.error("System is linux");
        }
        logger.error("My Coprocessor work");
        logger.error("=============="+new File(".").getAbsolutePath()+"================ ");
    }

    @Override
    public void preGetOp(ObserverContext<RegionCoprocessorEnvironment> e, Get get, List<Cell> results) throws IOException {
        byte[] copFamily  = Bytes.toBytes("copFamily");
        byte[] copColumn  = Bytes.toBytes("copColumn");
        KeyValue cell = new KeyValue(get.getRow(),copFamily,copColumn,Bytes.toBytes("@@HHHH@@"));
        results.add(cell);
        logger.debug("My coprocessor is Working!");
    }


    public static void main(String[] args) throws IOException {
        System.out.println("Try to Write Line to local File");
    }
}
