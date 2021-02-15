package com.jesse.db;

import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class Manager implements Runnable, AutoCloseable {
	private List<PooledConnection> list = new ArrayList<>();

	private int minCount = 1;
	private int maxCount = 10;
	private int maxWaitForConnection = 5; // in minutes
	private int retireAfterIdle = 30; // in minutes

	private Thread cleanupProcess;
	private boolean isStopThread = false;
	private LocalDateTime lastRunTime;

	private Logger logger;

	public Manager() {
		this(1);
	}

	public Manager(int minCount) {
		this.setMinCount(minCount);
		this.logger = Logger.getLogger(this.getClass().getName());
		this.cleanupProcess = new Thread(this);
		this.cleanupProcess.start();
	}

	public Connection getConnection() throws TimeoutException, SQLException, ClassNotFoundException {
		initialize();

		PooledConnection result = null;
		Instant start = Instant.now();
		while (result == null) {
			synchronized (this.list) {
				// Get a connection from pool
				for (PooledConnection conn : this.list) {
					if (conn.isClosed() && !conn.isRealClosed()) {
						conn.assign();
						return conn;
					}
				}

				// Create a new one
				if (this.list.size() < this.maxCount) {
					Connection sqlcn = getSqlConnection();
					this.list.add(new PooledConnection(sqlcn));
				}
			}

			if (Duration.between(start, Instant.now()).toMinutes() >= this.maxWaitForConnection) {
				break;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		throw new TimeoutException("Cannot get an available connection.");
	}

	// Initialize the pool with min number of connections
	private void initialize() {
		int count = this.minCount - this.list.size();
		for (int i = 0; i < count; i++) {
			Connection sqlcn;
			try {
				sqlcn = getSqlConnection();
				this.list.add(new PooledConnection(sqlcn));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private Connection getSqlConnection() throws SQLException, ClassNotFoundException {
		return getSqlConnection(null);
	}

	private Connection getSqlConnection(String dbserver) throws ClassNotFoundException, SQLException {
		final String MYSQL = "mysql";
		final String SQLSERVER = "SQLServer";

		if (dbserver == null || dbserver.trim().length() == 0) {
			dbserver = MYSQL;
		}

		Connection conn;
		String driver, url, user, pwd;

		if (dbserver.equals(MYSQL)) {
			driver = "com.mysql.cj.jdbc.Driver";
			url = "jdbc:mysql://localhost:3306/test?serverTimezone=UTC&useLegacyDatetimeCode=false";
			user = "root";
			pwd = "@ctive123";
		}
		else if (dbserver.equals(SQLSERVER)) {
			driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
			url = "jdbc:sqlserver://localhost:1433;DatabaseName=Test";
			user = "recware";
			pwd = "safari";
		}
		else {
			throw new InvalidParameterException("Invalid db server type: " + (dbserver == null ? "null" : dbserver));
		}
		Class.forName(driver);
		conn = DriverManager.getConnection(url, user, pwd);
		if (conn == null) {
			throw new NullPointerException("Connection is null");
		}
		return conn;
	}

	public void close() {
		this.clear();
		this.setStopThread(true);
		try {
			this.cleanupProcess.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public void clear() {
		for (int i = this.list.size() - 1; i >= 0; i--) {
			try {
				this.list.get(i).realClose();
				this.list.remove(i);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private String showList() {
		List<String> result = new ArrayList<>();
		result.add("Connections in the pool:");
		for (int i = 0; i < this.list.size(); i++) {
			result.add(String.format("#%d: %s", i + 1, this.list.get(i).toString()));
		}
		return String.join("\r\n", result);
	}

	public String status() {
		if (this.list.isEmpty()) {
			return "No connection in the pool.";
		}
		else if (this.list.size() == 1) {
			return "A single connection in the pool: " + this.list.get(0).toString();
		}
		else if (this.list.size() <= 10) {
			return showList();
		}

		List<String> messages = new ArrayList<>();
		int availableCount = 0;
		PooledConnection lastAvailable = null;
		PooledConnection oldestUsed = null;
		PooledConnection newestUsed = null;

		for (PooledConnection conn : this.list) {
			if (conn.isClosed()) {
				availableCount++;
				lastAvailable = conn;
			}

			if (conn.getLastUsedTime() != null) {
				if (oldestUsed == null || oldestUsed.getLastUsedTime().isAfter(conn.getLastUsedTime())) {
					oldestUsed = conn;
				}
				if (newestUsed == null || newestUsed.getLastUsedTime().isBefore(conn.getLastUsedTime())) {
					newestUsed = conn;
				}
			}
		}

		messages.add(String.format("Available connections: %d of %d", availableCount, this.list.size()));
		messages.add(String.format("The first: %s", this.list.get(0).toString()));
		messages.add(String.format("The last : %s", this.list.get(this.list.size() - 1).toString()));
		if (lastAvailable != null) {
			messages.add(String.format("The last available: (#%d) %s", this.list.indexOf(lastAvailable) + 1, lastAvailable.toString()));
		}
		if (newestUsed != null) {
			messages.add(String.format("The nearest used: (#%d) %s", this.list.indexOf(newestUsed) + 1, newestUsed.toString()));
		}
		if (oldestUsed != null && oldestUsed.getLastUsedTime().isBefore(newestUsed.getLastUsedTime())) {
			messages.add(String.format("The farthest used: (#%d) %s", this.list.indexOf(oldestUsed) + 1, oldestUsed.toString()));
		}

		if (this.lastRunTime == null) {
			messages.add("No cleanup process is done yet.");
		}
		else {
			messages.add("Cleanup process is done at " + this.lastRunTime.format(DateTimeFormatter.ofPattern("MMM d HH:mm:ss")));
		}

		return String.join("\r\n", messages);
	}

	// Connection cleanup process
	// Remove connections that have not been used for a long time.
	// It's allowed to remove connections to zero regardless the value of min count.
	@Override
	public void run() {
		int interval = 60; // in seconds
		logger.info(String.format("Connection cleanup process started (interval = %d sec)", interval));

		while (!this.isStopThread) {
			try {
				Thread.sleep(1000 * interval);

				for (int i = this.list.size() - 1; i >= 0; i--) {
					PooledConnection conn = this.list.get(i);
					if (conn.isClosed()) {
						LocalDateTime base = conn.getLastUsedTime();
						if (base == null) {
							base = conn.getCreatedTime();
						}
						if (Duration.between(base, LocalDateTime.now()).toMinutes() >= this.retireAfterIdle) {
							conn.realClose();
							this.list.remove(i);
							logger.info("Connection removed: " + conn.toString());
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				this.lastRunTime = LocalDateTime.now();
			}
		}

		logger.info("Connection cleanup process started");
	}

	public int getMinCount() {
		return minCount;
	}

	public void setMinCount(int count) {
		if (count < 1 || count == this.minCount) return;

		this.minCount = count;
		if (this.minCount > this.maxCount) {
			this.maxCount = this.minCount;
		}
	}

	public int getMaxCount() {
		return maxCount;
	}

	public void setMaxCount(int count) {
		if (count < this.minCount || count == this.maxCount) return;

		this.maxCount = count;
	}

	public boolean isStopThread() {
		return isStopThread;
	}

	public void setStopThread(boolean stop) {
		this.isStopThread = stop;
	}
}