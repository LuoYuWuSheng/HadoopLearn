package site.luoyu;

/**
 * Created by xd on 2016/11/11.
 */
import com.google.common.io.Closeables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.tephra.TransactionConflictException;
import org.apache.tephra.TransactionContext;
import org.apache.tephra.TransactionFailureException;
import org.apache.tephra.TransactionSystemClient;
import org.apache.tephra.distributed.TransactionServiceClient;
import org.apache.tephra.hbase.TransactionAwareHTable;
import org.apache.tephra.hbase.coprocessor.TransactionProcessor;
import org.apache.tephra.runtime.ConfigModule;
import org.apache.tephra.runtime.DiscoveryModules;
import org.apache.tephra.runtime.TransactionClientModule;
import org.apache.tephra.runtime.TransactionModules;
import org.apache.tephra.runtime.ZKModule;
import org.apache.tephra.util.ConfigurationFactory;
import org.apache.twill.zookeeper.ZKClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BalanceBooks implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(BalanceBooks.class);

    private static final int MAX_AMOUNT = 100;
    private static final byte[] TABLE = Bytes.toBytes("testbalances");
    private static final byte[] FAMILY = Bytes.toBytes("f");
    private static final byte[] COL = Bytes.toBytes("b");

    private final int totalClients;
    private final int iterations;

    private Configuration conf;
    private ZKClientService zkClient;
    private TransactionServiceClient txClient;
    private HConnection conn;

    public BalanceBooks(int totalClients, int iterations) {
        this(totalClients, iterations, new ConfigurationFactory().get());
    }

    public BalanceBooks(int totalClients, int iterations, Configuration conf) {
//        Configuration LocalConf = HBaseConfiguration.create();
        URL hdfsConfigURL = CURDdemo.class.getResource("/HDFSOperator.xml");
        URL configURL = CURDdemo.class.getResource("/HBaseOperator.xml");
        conf.addResource(configURL);
        this.totalClients = totalClients;
        this.iterations = iterations;
        this.conf = conf;
    }

    /**
     * Sets up common resources required by all clients.
     */
    public void init() throws IOException {
        Injector injector = Guice.createInjector(
                new ConfigModule(conf),
                new ZKModule(),
                new DiscoveryModules().getDistributedModules(),
                new TransactionModules().getDistributedModules(),
                new TransactionClientModule()
        );

        zkClient = injector.getInstance(ZKClientService.class);
        zkClient.startAndWait();
        txClient = injector.getInstance(TransactionServiceClient.class);

        createTableIfNotExists(conf, TABLE, new byte[][]{ FAMILY });
        conn = HConnectionManager.createConnection(conf);
    }

    /**
     * Runs all clients and waits for them to complete.
     */
    public void run() throws IOException, InterruptedException {
        List<Client> clients = new ArrayList<>(totalClients);
        for (int i = 0; i < totalClients; i++) {
            Client c = new Client(i, totalClients, iterations);
            c.init(txClient, conn.getTable(TABLE));
            c.start();
            clients.add(c);
        }

        for (Client c : clients) {
            c.join();
            Closeables.closeQuietly(c);
        }
    }

    /**
     * Validates the current state of the data stored at the end of the test.  Each update by a client consists of two
     * parts: a withdrawal of a random amount from a randomly select other account, and a corresponding to deposit to
     * the client's own account.  So, if all the updates were performed consistently (no partial updates or partial
     * rollbacks), then the total sum of all balances at the end should be 0.
     */
    public boolean verify() {
        boolean success = false;
        try {
            TransactionAwareHTable table = new TransactionAwareHTable(conn.getTable(TABLE));
            TransactionContext context = new TransactionContext(txClient, table);

            LOG.info("VERIFYING BALANCES");
            context.start();
            long totalBalance = 0;

            try (ResultScanner scanner = table.getScanner(new Scan())) {
                for (Result r : scanner) {
                    if (!r.isEmpty()) {
                        int rowId = Bytes.toInt(r.getRow());
                        long balance = Bytes.toLong(r.getValue(FAMILY, COL));
                        totalBalance += balance;
                        LOG.info("Client #{}: balance = ${}", rowId, balance);
                    }
                }
            }
            if (totalBalance == 0) {
                LOG.info("PASSED!");
                success = true;
            } else {
                LOG.info("FAILED! Total balance should be 0 but was {}", totalBalance);
            }
            context.finish();
        } catch (Exception e) {
            LOG.error("Failed verification check", e);
        }
        return success;
    }

    /**
     * Frees up the underlying resources common to all clients.
     */
    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (IOException ignored) { }

        if (zkClient != null) {
            zkClient.stopAndWait();
        }
    }

    protected void createTableIfNotExists(Configuration conf, byte[] tableName, byte[][] columnFamilies)
            throws IOException {
        try (HBaseAdmin admin = new HBaseAdmin(conf)) {
            HTableDescriptor desc = new HTableDescriptor(TableName.valueOf(tableName));
            for (byte[] family : columnFamilies) {
                HColumnDescriptor columnDesc = new HColumnDescriptor(family);
                columnDesc.setMaxVersions(Integer.MAX_VALUE);
                desc.addFamily(columnDesc);
            }
            desc.addCoprocessor(TransactionProcessor.class.getName());
            admin.createTable(desc);
        }
    }

    public static void main(String[] args) {
        args = new String[2];
        args[0] = "5";
        args[1] = "100";
        if (args.length != 2) {
            System.err.println("Usage: java " + BalanceBooks.class.getName() + " <num clients> <iterations>");
            System.err.println("\twhere <num clients> >= 2");
            System.exit(1);
        }

        try (BalanceBooks bb = new BalanceBooks(Integer.parseInt(args[0]), Integer.parseInt(args[1]))) {
            bb.init();
            bb.run();
            bb.verify();
        } catch (Exception e) {
            LOG.error("Failed during BalanceBooks run", e);
        }
    }

    /**
     * Represents a single client actor in the test.  Each client runs as a separate thread.
     *
     * For the given number of iterations, the client will:
     * <ol>
     *   <li>select a random other client from which to withdraw</li>
     *   <li>select a random amount from 0 to MAX_AMOUNT</li>
     *   <li>start a new transaction and: deduct the amount from the other client's acccount, and deposit
     *       the same amount to its own account.</li>
     * </ol>
     *
     * Since multiple clients operate concurrently and contend over a set of constrained resources
     * (the client accounts), it is expected that a portion of the attempted transactions will encounter
     * conflicts, due to a simultaneous deduction from or deposit to one the same accounts which has successfully
     * committed first.  In this case, the updates from the transaction encountering the conflict should be completely
     * rolled back, leaving the data in a consistent state.
     */
    private static class Client extends Thread implements Closeable {
        private final int id;
        private final int totalClients;
        private final int iterations;

        private final Random random = new Random();

        private TransactionContext txContext;
        private TransactionAwareHTable txTable;


        public Client(int id, int totalClients, int iterations) {
            this.id = id;
            this.totalClients = totalClients;
            this.iterations = iterations;
        }

        /**
         * Sets up any resources needed by the individual client.
         *
         * @param txClient the transaction client to use in accessing the transaciton service
         * @param table the HBase table instance to use for accessing storage
         */
        public void init(TransactionSystemClient txClient, HTableInterface table) {
            txTable = new TransactionAwareHTable(table);
            txContext = new TransactionContext(txClient, txTable);
        }

        public void run() {
            try {
                for (int i = 0; i < iterations; i++) {
                    runOnce();
                }
            } catch (TransactionFailureException e) {
                LOG.error("Client #{}: Failed on exception", id, e);
            }
        }

        /**
         * Runs a single iteration of the client logic.
         */
        private void runOnce() throws TransactionFailureException {
            int withdrawee = getNextWithdrawee();
            int amount = getAmount();

            try {
                txContext.start();
                long withdraweeBalance = getCurrentBalance(withdrawee);
                long ownBalance = getCurrentBalance(id);
                long withdraweeNew = withdraweeBalance - amount;
                long ownNew = ownBalance + amount;

                setBalance(withdrawee, withdraweeNew);
                setBalance(id, ownNew);
                LOG.debug("Client #{}: Withdrew ${} from #{}; withdrawee old={}, new={}; own old={}, new={}",
                        id, amount, withdrawee, withdraweeBalance, withdraweeNew, ownBalance, ownNew);
                txContext.finish();

            } catch (IOException ioe) {
                LOG.error("Client #{}: Unhandled client failure", id, ioe);
                txContext.abort();
            } catch (TransactionConflictException tce) {
                LOG.debug("CONFLICT: client #{} attempting to withdraw from #{}", id, withdrawee);
                txContext.abort(tce);
            } catch (TransactionFailureException tfe) {
                LOG.error("Client #{}: Unhandled transaction failure", id, tfe);
                txContext.abort(tfe);
            }
        }

        private long getCurrentBalance(int id) throws IOException {
            Result r = txTable.get(new Get(Bytes.toBytes(id)));
            byte[] balanceBytes = r.getValue(FAMILY, COL);
            if (balanceBytes == null) {
                return 0;
            }
            return Bytes.toLong(balanceBytes);
        }

        private void setBalance(int id, long balance) throws IOException {
            txTable.put(new Put(Bytes.toBytes(id)).add(FAMILY, COL, Bytes.toBytes(balance)));
        }

        private int getNextWithdrawee() {
            int next;
            do {
                next = random.nextInt(totalClients);
            } while (next == id);
            return next;
        }

        private int getAmount() {
            return random.nextInt(MAX_AMOUNT);
        }

        public void close() throws IOException {
            txTable.close();
        }
    }
}

