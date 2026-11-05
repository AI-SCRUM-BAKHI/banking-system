package com.scrumai.fraud.scan;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class TransactionScanLock {
  private static final long LOCK_KEY = 734920261104L;
  private final DataSource dataSource;

  public TransactionScanLock(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public Lease tryAcquire() throws SQLException {
    Connection connection = dataSource.getConnection();
    try {
      if (execute(connection, "SELECT pg_try_advisory_lock(?)")) {
        return new Lease(connection);
      }
      connection.close();
      return null;
    } catch (SQLException exception) {
      // The server might have acquired the lock before the connection failed.
      discard(connection, exception);
      throw exception;
    }
  }

  private static boolean execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.prepareStatement(sql)) {
      statement.setLong(1, LOCK_KEY);
      try (var rows = statement.executeQuery()) {
        return rows.next() && rows.getBoolean(1);
      }
    }
  }

  private static void discard(Connection connection, SQLException original) {
    try {
      connection.abort(Runnable::run);
      // Once the physical session is terminated, the proxy can safely return to the pool.
      connection.close();
    } catch (SQLException exception) {
      original.addSuppressed(exception);
    }
    // If abort failed, do not recycle a session whose lock ownership is uncertain.
  }

  public static final class Lease implements AutoCloseable {
    private final Connection connection;

    private Lease(Connection connection) {
      this.connection = connection;
    }

    public void verify() throws SQLException {
      // Fail closed before starting the next transaction if the dedicated session was lost.
      if (!connection.isValid(5)) {
        throw new SQLException("Transaction scan lock connection lost");
      }
    }

    @Override
    public void close() throws SQLException {
      try {
        if (!execute(connection, "SELECT pg_advisory_unlock(?)")) {
          throw new SQLException("Transaction scan lock was not held");
        }
      } catch (SQLException exception) {
        discard(connection, exception);
        throw exception;
      }
      connection.close();
    }
  }
}
