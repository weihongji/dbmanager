package com.jesse.db;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class PooledConnection implements Connection {
	private Connection physicalConn;
	private boolean isClosed; // closed indicates available
	private LocalDateTime createdTime;
	private LocalDateTime lastUsedTime;
	private long usedCount;

	public LocalDateTime getCreatedTime() {
		return createdTime;
	}

	public LocalDateTime getLastUsedTime() {
		return lastUsedTime;
	}

	public long getUsedCount() {
		return usedCount;
	}

	public PooledConnection(Connection connection) {
		this.physicalConn = connection;
		this.isClosed = true;
		this.createdTime = LocalDateTime.now();
		this.usedCount = 0;
	}

	public synchronized boolean assign() {
		if (this.isClosed) {
			this.isClosed = false;
			this.lastUsedTime = LocalDateTime.now();
			this.usedCount++;
			return true;
		}
		else {
			return false; // Connection is in use.
		}
	}

	public void realClose() throws SQLException {
		close();
		this.physicalConn.close();
	}

	public boolean isRealClosed() throws SQLException {
		return this.physicalConn.isClosed();
	}
	
	private void breakIfClosed() throws SQLException {
		if (this.isClosed) throw new SQLException("Error. Try to use a closed connection.");
	} 

	@Override
	public String toString() {
		String status = this.isClosed ? "Available" : "Engaged";
		try {
			if (this.physicalConn != null && this.isRealClosed()) {
				status = "Closed";
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		String pattern = (this.createdTime.toLocalDate().isBefore(LocalDate.now()) ? "MMM d " : "") + "HH:mm:ss";
		String timeText = "created at: " + this.createdTime.format(DateTimeFormatter.ofPattern(pattern));

		if (this.lastUsedTime != null) {
			pattern = (this.lastUsedTime.toLocalDate().isBefore(LocalDate.now()) ? "MMM d " : "") + "HH:mm:ss";
			timeText += ", last used: " + this.lastUsedTime.format(DateTimeFormatter.ofPattern(pattern));
		}

		return String.format("%s, %s, usage: %d", status, timeText, this.usedCount);
	}

	@Override
	public Statement createStatement() throws SQLException {
		breakIfClosed();
		return this.physicalConn.createStatement();
	}

	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareStatement(sql);
	}

	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareCall(sql);
	}

	@Override
	public String nativeSQL(String sql) throws SQLException {
		breakIfClosed();
		return this.physicalConn.nativeSQL(sql);
	}

	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
		breakIfClosed();
		this.physicalConn.setAutoCommit(autoCommit);
	}

	@Override
	public boolean getAutoCommit() throws SQLException {
		breakIfClosed();
		return this.physicalConn.getAutoCommit();
	}

	@Override
	public void commit() throws SQLException {
		breakIfClosed();
		this.physicalConn.commit();
	}

	@Override
	public void rollback() throws SQLException {
		breakIfClosed();
		this.physicalConn.rollback();
	}

	@Override
	public void close() {
		this.isClosed = true;
	}

	@Override
	public boolean isClosed() {
		return this.isClosed;
	}

	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return this.physicalConn.getMetaData();
	}

	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		this.physicalConn.setReadOnly(readOnly);
	}

	@Override
	public boolean isReadOnly() throws SQLException {
		return this.physicalConn.isReadOnly();
	}

	@Override
	public void setCatalog(String catalog) throws SQLException {
		this.physicalConn.setCatalog(catalog);
	}

	@Override
	public String getCatalog() throws SQLException {
		return this.physicalConn.getCatalog();
	}

	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		this.physicalConn.setTransactionIsolation(level);
	}

	@Override
	public int getTransactionIsolation() throws SQLException {
		return this.physicalConn.getTransactionIsolation();
	}

	@Override
	public SQLWarning getWarnings() throws SQLException {
		return this.physicalConn.getWarnings();
	}

	@Override
	public void clearWarnings() throws SQLException {
		this.physicalConn.clearWarnings();
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		breakIfClosed();
		return this.physicalConn.createStatement(resultSetType, resultSetConcurrency);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return this.physicalConn.getTypeMap();
	}

	@Override
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		breakIfClosed();
		this.physicalConn.setTypeMap(map);
	}

	@Override
	public void setHoldability(int holdability) throws SQLException {
		breakIfClosed();
		this.physicalConn.setHoldability(holdability);
	}

	@Override
	public int getHoldability() throws SQLException {
		return this.physicalConn.getHoldability();
	}

	@Override
	public Savepoint setSavepoint() throws SQLException {
		breakIfClosed();
		return this.physicalConn.setSavepoint();
	}

	@Override
	public Savepoint setSavepoint(String name) throws SQLException {
		breakIfClosed();
		return this.physicalConn.setSavepoint(name);
	}

	@Override
	public void rollback(Savepoint savepoint) throws SQLException {
		breakIfClosed();
		this.physicalConn.rollback(savepoint);
	}

	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		breakIfClosed();
		this.physicalConn.releaseSavepoint(savepoint);
	}

	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		breakIfClosed();
		return this.physicalConn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareStatement(sql, autoGeneratedKeys);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareStatement(sql, columnIndexes);
	}

	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		breakIfClosed();
		return this.physicalConn.prepareStatement(sql, columnNames);
	}

	@Override
	public Clob createClob() throws SQLException {
		breakIfClosed();
		return this.physicalConn.createClob();
	}

	@Override
	public Blob createBlob() throws SQLException {
		breakIfClosed();
		return this.physicalConn.createBlob();
	}

	@Override
	public NClob createNClob() throws SQLException {
		breakIfClosed();
		return this.physicalConn.createNClob();
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		breakIfClosed();
		return this.physicalConn.createSQLXML();
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		breakIfClosed();
		return this.physicalConn.isValid(timeout);
	}

	@Override
	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		this.physicalConn.setClientInfo(name, value);
	}

	@Override
	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		this.physicalConn.setClientInfo(properties);
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		breakIfClosed();
		return this.physicalConn.getClientInfo(name);
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		return this.physicalConn.getClientInfo();
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		breakIfClosed();
		return this.physicalConn.createArrayOf(typeName, elements);
	}

	@Override
	public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		breakIfClosed();
		return this.physicalConn.createStruct(typeName, attributes);
	}

	@Override
	public void setSchema(String schema) throws SQLException {
		breakIfClosed();
		this.physicalConn.setSchema(schema);
	}

	@Override
	public String getSchema() throws SQLException {
		breakIfClosed();
		return this.physicalConn.getSchema();
	}

	@Override
	public void abort(Executor executor) throws SQLException {
		breakIfClosed();
		this.physicalConn.abort(executor);
	}

	@Override
	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		breakIfClosed();
		this.physicalConn.setNetworkTimeout(executor, milliseconds);
	}

	@Override
	public int getNetworkTimeout() throws SQLException {
		breakIfClosed();
		return this.physicalConn.getNetworkTimeout();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		breakIfClosed();
		return this.physicalConn.unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		breakIfClosed();
		return this.physicalConn.isWrapperFor(iface);
	}
}