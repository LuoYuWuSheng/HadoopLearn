package site.luoyu;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;

/**
 * ClouodgisMeta Tester.
 *
 * @author 张洋
 * @version 1.0
 * @since <pre>05/03/2017</pre>
 */
public class ClouodgisMetaTest {

    private ClouodgisMeta test = new ClouodgisMeta();

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
    }

    @Test
    public void getMeta() throws IOException {
        test.getMeta();
    }
} 
