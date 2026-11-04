package com.scrumai.fraud.scan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class TransactionScanLockTest {
  @Test
  void abortsPhysicalSessionBeforeReturningProxyWhenUnlockFails() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    var acquire = mock(PreparedStatement.class);
    var rows = mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(acquire);
    when(acquire.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true);
    when(rows.getBoolean(1)).thenReturn(true);
    when(connection.prepareStatement("SELECT pg_advisory_unlock(?)"))
        .thenThrow(new SQLException("connection lost"));
    var lease = new TransactionScanLock(source).tryAcquire();
    assertThatThrownBy(lease::close).isInstanceOf(SQLException.class);
    var order = org.mockito.Mockito.inOrder(connection);
    order.verify(connection).abort(any());
    order.verify(connection).close();
  }

  @Test
  void doesNotRecyclePotentiallyLockedSessionIfAbortAlsoFails() throws Exception {
    var source = mock(DataSource.class);
    var connection = mock(Connection.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)"))
        .thenThrow(new SQLException("connection lost during acquisition"));
    doThrow(new SQLException("abort failed")).when(connection).abort(any());
    assertThatThrownBy(() -> new TransactionScanLock(source).tryAcquire())
        .isInstanceOf(SQLException.class);
    verify(connection).abort(any());
    verify(connection, never()).close();
  }
}
