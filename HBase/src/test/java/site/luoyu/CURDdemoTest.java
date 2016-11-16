package site.luoyu;

import org.junit.Test; 
import org.junit.Before; 
import org.junit.After;
import site.luoyu.CURDdemo;

import java.io.IOException;

/** 
* CURDdemo Tester. 
* 
* @author <Authors name> 
* @since <pre>11/14/2016</pre> 
* @version 1.0 
*/ 
public class CURDdemoTest {

    CURDdemo tephraTest;
    @Before
    public void before() throws Exception {
        tephraTest = new CURDdemo();
    }

    @After
    public void after() throws Exception {
    }

    /**
    * test basic put if table doesn't have a family will it fail?
    */
    @Test
    public void testMain() throws Exception {
        tephraTest.PutData();
    }

    @Test
    public void testConfig(){
        CURDdemo config = new CURDdemo();
        String address = config.getConfiguration().get("data.tx.bind.address");
        String local = config.getConfiguration().get("data.tx.bind.address");
    }

    @Test
    public void TephraTx() throws IOException {
        tephraTest.TephraDemo();
    }
} 
