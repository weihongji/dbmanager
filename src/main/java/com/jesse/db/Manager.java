package com.jesse.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("BusyWait")
public class Manager implements Runnable, AutoCloseable {
	private final Connector connector;
	private final List<PooledConnection> list = new ArrayList<>();
	private int lastConnectionId = 0;
	private final LocalDateTime dateStamp;

	private int minSize = 1;
	private int maxSize = 100;
	private int maxWaitForConnection = 5; // Max time to wait for getting a connection if cannot get one immediately. (in minutes)
	private int refreshToKeepAlive = 30; // Run a dummy query to keep communication with server if idle long enough. (in minutes)
	private int retireAfterIdle = 30; // When number of connections exceeds the min size, connections idle long enough will be removed. (in minutes)
	private int retireAfterStale = 10 * 24 * 60; // Max time when a connection can stay in pool. (in minutes)
	private int timeoutMinute = 60; // Max time when a connection can keep in used. (in minutes)
	private String dummyQuery = "select 1";

	// Thread
	private int interval = 5 * 60; // in seconds
	private final Thread cleanupProcess;
	private boolean isStopThread = false;
	private LocalDateTime lastRunTime;

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	public Manager(Connector connector) {
		this(connector, 1);
	}

	public Manager(Connector connector, int minSize) {
		this.connector = connector;
		setMinSize(minSize);
		cleanupProcess = new Thread(this);
		cleanupProcess.start();
		Level level = Level.parse(PropertyUtil.getInstance().getProperty("loglevel", "INFO"));
		logger.setLevel(level);
		dateStamp = LocalDateTime.now();
	}

	public Connection getConnection() throws TimeoutException, SQLException, ClassNotFoundException {
		initialize();

		Instant start = Instant.now();
		while (true) {
			synchronized (list) {
				// Get a connection from pool.
				for (int i = list.size() - 1; i >= 0; i--) {
					PooledConnection conn = list.get(i);
					if (conn.isClosed() && !conn.isRealClosed()) {
						if (conn.assign()) {
							return conn;
						}
					}
				}

				// Create a new one
				if (list.size() < maxSize) {
					Connection sqlcn = getSqlConnection();
					list.add(new PooledConnection(sqlcn, this));
				}
			}

			if (Duration.between(start, Instant.now()).toMinutes() >= getMaxWaitForConnection()) {
				throw new TimeoutException(String.format("Cannot get an available connection within %d minutes.", getMaxWaitForConnection()));
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	// Initialize the pool with min number of connections
	private synchronized void initialize() {
		int count = minSize - list.size();
		for (int i = 0; i < count; i++) {
			Connection sqlcn;
			try {
				sqlcn = getSqlConnection();
				list.add(new PooledConnection(sqlcn, this));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private Connection getSqlConnection() throws ClassNotFoundException, SQLException {
		Class.forName(connector.getDriver());
		Connection conn = DriverManager.getConnection(connector.getUrl(), connector.getUser(), connector.getPassword());
		if (conn == null) {
			throw new NullPointerException("Connection is null");
		}
		return conn;
	}

	public void close() {
		clear();
		setStopThread(true);
		try {
			cleanupProcess.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public void clear() {
		for (int i = list.size() - 1; i >= 0; i--) {
			list.get(i).safeRealClose();
			list.remove(i);
		}
	}

	public String getListAsString() {
		List<String> result = new ArrayList<>();
		result.add("Connections in the pool:");
		for (PooledConnection pooledConnection : list) {
			result.add(pooledConnection.toString());
		}
		return String.join("\r\n", result);
	}

	public String getStatus() {
		if (list.isEmpty()) {
			return "No connection in the pool.";
		}
		else if (list.size() == 1) {
			return "A single connection in the pool: " + list.get(0).toString();
		}
		else if (list.size() <= 10) {
			return getListAsString();
		}

		List<String> messages = new ArrayList<>();
		int availableCount = 0;
		PooledConnection lastAvailable = null;
		PooledConnection oldestUsed = null;
		PooledConnection newestUsed = null;

		for (PooledConnection conn : list) {
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

		messages.add(String.format("Available connections: %d of %d", availableCount, list.size()));
		messages.add(String.format("The first: %s", list.get(0).toString()));
		messages.add(String.format("The last : %s", list.get(list.size() - 1).toString()));
		if (lastAvailable != null) {
			messages.add(String.format("The last available: (#%d) %s", list.indexOf(lastAvailable) + 1, lastAvailable.toString()));
		}
		if (newestUsed != null) {
			messages.add(String.format("The nearest used: (#%d) %s", list.indexOf(newestUsed) + 1, newestUsed.toString()));
		}
		if (oldestUsed != null && oldestUsed.getLastUsedTime().isBefore(newestUsed.getLastUsedTime())) {
			messages.add(String.format("The farthest used: (#%d) %s", list.indexOf(oldestUsed) + 1, oldestUsed.toString()));
		}

		if (lastRunTime == null) {
			messages.add("No cleanup process is done yet.");
		}
		else {
			messages.add("Cleanup process is done at " + lastRunTime.format(DateTimeFormatter.ofPattern("MMM d HH:mm:ss")));
		}

		return String.join("\r\n", messages);
	}

	// Connection cleanup process
	// Remove connections that have not been used for a long time.
	@Override
	public void run() {
		logger.info(String.format("Connection cleanup process started (interval = %d sec)", interval));
		long refreshInterval = 1000 * interval; // Time before next check on connection list
		long sleepInterval = 1000; // Time before next check on loop control (variable isStopThread).
		long elapsed = 0;
		while (!isStopThread) {
			try {
				Thread.sleep(sleepInterval);

				elapsed += sleepInterval;
				if (elapsed < refreshInterval) {
					continue;
				}

				// Health check
				for (int i = 0; i < list.size(); i++) {
					PooledConnection conn = list.get(i);
					// Has been opened for too long time.
					if (!conn.isClosed() && Duration.between(conn.getLastUsedTime(), LocalDateTime.now()).toMinutes() > timeoutMinute) {
						conn.safeRealClose();
						list.remove(i--);
						logger.info("Removed connection due to timeout: " + conn.toString());
						continue;
					}
					// Has been created for too long time.
					if (conn.isClosed() && Duration.between(conn.getDateStamp(), LocalDateTime.now()).toMinutes() > retireAfterStale) {
						conn.safeRealClose();
						list.remove(i--);
						logger.info("Removed connection due to stale: " + conn.toString());
						continue;
					}
					// Refresh physical connection to keep alive
					if (conn.getIdleTime().toMinutes() >= refreshToKeepAlive) {
						if (!conn.isRealClosed()) {
							try {
								Statement stmt = conn.createStatement();
								stmt.execute(dummyQuery);
								logger.info("Refreshed connection: " + conn.toString());
							} catch (Exception e) {
								conn.safeRealClose();
								list.remove(i--);
								logger.info("Removed connection due to unavailable: " + conn.toString());
								//continue; Need to uncomment this statement if any statement is added after it in this loop.
							}
						}
					}
				}

				// Remove long idle connections if total number is larger than the min size.
				for (int i = 0; i < list.size() && list.size() > minSize; i++) {
					PooledConnection conn = list.get(i);
					if (conn.isClosed()) {
						if (conn.getIdleTime().toMinutes() >= retireAfterIdle) {
							conn.safeRealClose();
							list.remove(i);
							logger.info("Removed connection due to long idle time: " + conn.toString());
							//continue; Need to uncomment this statement if any statement is added after it in this loop.
						}
					}
				}

				// Add new connections in case we deleted too many.
				if (list.size() < minSize) {
					initialize();
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				lastRunTime = LocalDateTime.now();
			}
		}

		logger.info("Connection cleanup process stopped");
	}

	public LocalDateTime getDateStamp() {
		return dateStamp;
	}

	public int getMinSize() {
		return minSize;
	}

	public void setMinSize(int size) {
		if (size >= 1 && size != minSize) {
			minSize = size;
			if (minSize > maxSize) {
				maxSize = minSize;
			}
		}
	}

	public int getMaxSize() {
		return maxSize;
	}

	public void setMaxSize(int size) {
		if (size >= minSize && size != maxSize) {
			maxSize = size;
		}
	}

	public int getMaxWaitForConnection() {
		return maxWaitForConnection;
	}

	public void setMaxWaitForConnection(int minutes) {
		maxWaitForConnection = minutes;
	}

	public int getRefreshToKeepAlive() {
		return refreshToKeepAlive;
	}

	public void setRefreshToKeepAlive(int minutes) {
		refreshToKeepAlive = minutes;
	}

	public int getRetireAfterIdle() {
		return retireAfterIdle;
	}

	public void setRetireAfterIdle(int minutes) {
		retireAfterIdle = minutes;
	}

	public int getRetireAfterStale() {
		return retireAfterStale;
	}

	public void setRetireAfterStale(int minutes) {
		retireAfterStale = minutes;
	}

	public int getTimeoutMinute() {
		return timeoutMinute;
	}

	public int setTimeoutMinute(int minutes) {
		return timeoutMinute = minutes;
	}

	public String getDummyQuery() {
		return dummyQuery;
	}

	public void setDummyQuery(String query) {
		dummyQuery = query;
	}

	public int getRefreshInterval() {
		return interval;
	}

	public boolean isStopThread() {
		return isStopThread;
	}

	public void setStopThread(boolean stop) {
		isStopThread = stop;
	}

	public int getSize() {
		return list.size();
	}

	public synchronized int getNextConnectionId() {
		return ++lastConnectionId;
	}
}
