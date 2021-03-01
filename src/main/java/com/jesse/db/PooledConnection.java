package com.jesse.db;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class PooledConnection implements Connection {
	private Connection physicalConn;
	private boolean isClosed; // closed indicates available
	private LocalDateTime dateStamp;
	private LocalDateTime lastUsedTime;
	private long usedCount;
	private final Manager manager;
	private final long id;

	public long getId() {
		return id;
	}

	public LocalDateTime getDateStamp() {
		return dateStamp;
	}

	public LocalDateTime getLastUsedTime() {
		return lastUsedTime;
	}

	public long getUsedCount() {
		return usedCount;
	}

	public Manager getManager() {
		return manager;
	}

	public PooledConnection(Connection connection) {
		this(connection, null);
	}

	public PooledConnection(Connection connection, Manager manager) {
		physicalConn = connection;
		isClosed = true;
		dateStamp = LocalDateTime.now();
		usedCount = 0;
		this.manager = manager;
		id = manager == null ? 0 : manager.getNextConnectionId();
	}

	public synchronized boolean assign() {
		if (isClosed) {
			isClosed = false;
			lastUsedTime = LocalDateTime.now();
			usedCount++;
			return true;
		}
		else {
			return false; // Connection is in use.
		}
	}

	public void realClose() throws SQLException {
		close();
		if (physicalConn != null) {
			physicalConn.close();
		}
	}

	public void safeRealClose() {
		try {
			close();
			if (physicalConn != null) {
				physicalConn.close();
			}
		} catch (Exception e) {
		}
	}

	public boolean isRealClosed() throws SQLException {
		return physicalConn == null || physicalConn.isClosed();
	}

	private void breakIfClosed() throws SQLException {
		if (isClosed) throw new SQLException("Error. Try to use a closed connection.");
	}

	public String getStatus() {
		String status = isClosed ? "Available" : "Engaged";
		try {
			if (physicalConn != null && isRealClosed()) {
				status = "Closed";
			}
		} catch (SQLException e) {
			e.printStackTrace();
			status = "Unknown";
		}
		return status;
	}

	public Duration getIdleTime() {
		LocalDateTime base = lastUsedTime;
		if (base == null) {
			base = dateStamp;
		}
		return Duration.between(base, LocalDateTime.now());
	}

	@Override
	public String toString() {
		String pattern = (dateStamp.toLocalDate().isBefore(LocalDate.now()) ? "MMM d " : "") + "HH:mm:ss";
		String timeText = "created at: " + dateStamp.format(DateTimeFormatter.ofPattern(pattern));

		if (lastUsedTime != null) {
			pattern = (lastUsedTime.toLocalDate().isBefore(LocalDate.now()) ? "MMM d " : "") + "HH:mm:ss";
			timeText += ", last used: " + lastUsedTime.format(DateTimeFormatter.ofPattern(pattern));
		}

		return String.format("#%d: %s, %s, usage: %d", id, getStatus(), timeText, getUsedCount());
	}

	@Override
	public Statement createStatement() throws SQLException {
		breakIfClosed();
		return physicalConn.createStatement();
	}

	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareStatement(sql);
	}

	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareCall(sql);
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		breakIfClosed();
		return physicalConn.nativeSQL(sql);
	}

	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		breakIfClosed();
		physicalConn.setAutoCommit(autoCommit);
	}

	@Override
	public boolean getAutoCommit() throws SQLException {
		breakIfClosed();
		return physicalConn.getAutoCommit();
	}

	@Override
	public void commit() throws SQLException {
		breakIfClosed();
		physicalConn.commit();
	}

	@Override
	public void rollback() throws SQLException {
		breakIfClosed();
		physicalConn.rollback();
	}

	@Override
	public void close() {
		isClosed = true;
	}

	@Override
	public boolean isClosed() {
		return isClosed;
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return physicalConn.getMetaData();
	}

	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		physicalConn.setReadOnly(readOnly);
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		return physicalConn.isReadOnly();
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		physicalConn.setCatalog(catalog);
	}

	@Override
	public String getCatalog() throws SQLException {
		return physicalConn.getCatalog();
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		physicalConn.setTransactionIsolation(level);
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		return physicalConn.getTransactionIsolation();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return physicalConn.getWarnings();
	}

	@Override
	public void clearWarnings() throws SQLException {
		physicalConn.clearWarnings();
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		breakIfClosed();
		return physicalConn.createStatement(resultSetType, resultSetConcurrency);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return physicalConn.getTypeMap();
	}

	@Override
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		breakIfClosed();
		physicalConn.setTypeMap(map);
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		breakIfClosed();
		physicalConn.setHoldability(holdability);
	}

	@Override
	public int getHoldability() throws SQLException {
		return physicalConn.getHoldability();
	}

	@Override
	public Savepoint setSavepoint() throws SQLException {
		breakIfClosed();
		return physicalConn.setSavepoint();
	}

	@Override
	public Savepoint setSavepoint(String name) throws SQLException {
		breakIfClosed();
		return physicalConn.setSavepoint(name);
	}

	@Override
	public void rollback(Savepoint savepoint) throws SQLException {
		breakIfClosed();
		physicalConn.rollback(savepoint);
	}

	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		breakIfClosed();
		physicalConn.releaseSavepoint(savepoint);
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		breakIfClosed();
		return physicalConn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareStatement(sql, autoGeneratedKeys);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareStatement(sql, columnIndexes);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		breakIfClosed();
		return physicalConn.prepareStatement(sql, columnNames);
	}

	@Override
	public Clob createClob() throws SQLException {
		breakIfClosed();
		return physicalConn.createClob();
	}

	@Override
	public Blob createBlob() throws SQLException {
		breakIfClosed();
		return physicalConn.createBlob();
	}

	@Override
	public NClob createNClob() throws SQLException {
		breakIfClosed();
		return physicalConn.createNClob();
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		breakIfClosed();
		return physicalConn.createSQLXML();
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		breakIfClosed();
		return physicalConn.isValid(timeout);
	}

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		physicalConn.setClientInfo(name, value);
	}

	@Override
	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		physicalConn.setClientInfo(properties);
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		breakIfClosed();
		return physicalConn.getClientInfo(name);
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		return physicalConn.getClientInfo();
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		breakIfClosed();
		return physicalConn.createArrayOf(typeName, elements);
	}

	@Override
	public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		breakIfClosed();
		return physicalConn.createStruct(typeName, attributes);
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		breakIfClosed();
		physicalConn.setSchema(schema);
	}

	@Override
	public String getSchema() throws SQLException {
		breakIfClosed();
		return physicalConn.getSchema();
	}

	@Override
	public void abort(Executor executor) throws SQLException {
		breakIfClosed();
		physicalConn.abort(executor);
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		breakIfClosed();
		physicalConn.setNetworkTimeout(executor, milliseconds);
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		breakIfClosed();
		return physicalConn.getNetworkTimeout();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		breakIfClosed();
		return physicalConn.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		breakIfClosed();
		return physicalConn.isWrapperFor(iface);
	}
}