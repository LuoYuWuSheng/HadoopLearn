package site.luoyu;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.util.Progressable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Computer user xd
 * Created by 张洋 on 2017/5/8.
 */
public class ReadFile {

    public static void main(String[] args) {
        Configuration conf = new Configuration();
        URL hdfsConfigURL = ReadFile.class.getResource("/HDFSOperator.xml");
//        URL hbaseConfigURL = ReadFile.class.getResource("/HBaseOperator.xml");
        conf.addResource(hdfsConfigURL);
        InputStream in = null;
        OutputStream os = null;
        try {
            URI writeURI = new URI("/zyWriteTemp");
            FileSystem fs = FileSystem.get(writeURI,conf);
//            fs.createNewFile(new Path(writeURI));
            os = fs.create(new Path(writeURI), new Progressable() {
                @Override
                public void progress() {
                    System.out.println(".");
                }
            });
            String data = "ZY write data to this file \n";
            os.write(data.getBytes());
            os.flush();
            os.close();

//            todo 这里输入的Host如果是StandBy将不能读取。那么应该怎么操作呢？
//            读入数据 InputStream实际返回的是FSInputStream;
            URI uri = new URI("/zyWriteTemp");
            in = fs.open(new Path(uri));
            IOUtils.copyBytes(in,System.out,4096,false);
            System.out.println("");

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        finally {
            assert in != null;
            assert os != null;
            try {
                in.close();
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
