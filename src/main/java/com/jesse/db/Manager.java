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

	private int minSize;
	private int maxSize;
	private int maxWaitForConnection; // Max time to wait for getting a connection if cannot get one immediately. (in minutes)
	private int timeoutMinute; // Max time when a connection can keep in used. (in minutes)
	private int retireAfterIdle; // When number of connections exceeds the min size, connections idle long enough will be removed. (in minutes)
	private int refreshToKeepAlive; // Run a dummy query to keep communication with server if idle long enough. (in minutes)
	private int retireAfterStale; // Max time when a connection can stay in pool. (in minutes)
	private int refreshInterval; // Time before next check on connection list. (in seconds).
								 // This time should not be larger than any of time setting above. In other words, the value should be 1 ~ 60.
								 // Otherwise, settings above cannot work accurately in minutes.
	private String dummyQuery = "select 1";

	private final Thread cleanupProcess;
	private boolean keepRunning = true;
	private LocalDateTime lastRunTime;

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	public Manager(Connector connector) {
		this.connector = connector;
		dateStamp = LocalDateTime.now();

		PropertyUtil properties = PropertyUtil.getInstance();
		minSize = properties.getPropertyInt("dbcm.minSize", 1);
		maxSize = properties.getPropertyInt("dbcm.maxSize", 100);
		maxWaitForConnection = properties.getPropertyInt("dbcm.maxWaitForConnection", 5);
		timeoutMinute = properties.getPropertyInt("dbcm.timeoutMinute", 60);
		retireAfterIdle = properties.getPropertyInt("dbcm.retireAfterIdle", 30);
		refreshToKeepAlive = properties.getPropertyInt("dbcm.refreshToKeepAlive", 30);
		retireAfterStale = properties.getPropertyInt("dbcm.retireAfterStale", 10 * 24 * 60); // 10 days
		refreshInterval = properties.getPropertyInt("dbcm.refreshInterval",60); // in seconds. Value should be 1 ~ 60.

		logger.setLevel(Level.parse(properties.getProperty("dbcm.logLevel", "INFO")));

		cleanupProcess = new Thread(this);
		cleanupProcess.start();
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
		setKeepRunning(false);
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
		return String.join(System.lineSeparator(), result);
	}

	public String getStatus() {
		List<String> messages = new ArrayList<>();

		// Connections
		if (list.isEmpty()) {
			messages.add("No connection in the pool.");
		}
		else if (list.size() == 1) {
			messages.add("A single connection in the pool: " + list.get(0).toString());
		}
		else if (list.size() <= 10) {
			messages.add(getListAsString());
		}
		else {
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
		}

		messages.add("");

		// Configurations
		messages.add("Manager was created at " + dateStamp.format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")));
		messages.add(String.format("Min/Max size: %d/%d", minSize, maxSize));
		messages.add(String.format("Request connection timeout: %d minutes", maxWaitForConnection));
		messages.add(String.format("Engage connection max time: %d minutes", timeoutMinute));
		messages.add(String.format("Clean up after idle: %d minutes", retireAfterIdle));
		messages.add(String.format("Refresh to keep alive every: %d minutes", refreshToKeepAlive));
		messages.add(String.format("Max life time: %d minutes", retireAfterStale));
		messages.add(String.format("Refresh every: %d seconds", refreshInterval));
		if (lastRunTime == null) {
			messages.add("Cleanup process has not started yet.");
		}
		else {
			messages.add("Cleanup process was done at " + lastRunTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")));
		}

		return String.join(System.lineSeparator(), messages);
	}

	// Connection cleanup process
	// Remove connections that have not been used for a long time.
	@Override
	public void run() {
		// Wait a bit before getting in the loop so that variables (e.g., refreshInterval) overridden by user codes can take effect.
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		logger.info(String.format("Connection cleanup process started (interval = %d sec)", refreshInterval));
		long sleepInterval = 1000; // Time before next check on loop control (variable keepRunning).
		long refreshIntervalMillis = 1000 * refreshInterval;
		long elapsed = 0;
		while (keepRunning) {
			try {
				Thread.sleep(sleepInterval);

				elapsed += sleepInterval;
				if (elapsed < refreshIntervalMillis) {
					continue;
				}

				elapsed = 0;
				lastRunTime = LocalDateTime.now();

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
								conn.assign();
								Statement stmt = conn.createStatement();
								stmt.execute(dummyQuery);
								conn.close();
								logger.info("Refreshed connection: " + conn.toString());
							} catch (Exception e) {
								String connString = conn.toString();
								conn.safeRealClose();
								list.remove(i--);
								logger.info("Removed connection due to unavailable: " + connString);
								logger.warning(e.getMessage());
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
		return refreshInterval;
	}

	public void setRefreshInterval(int seconds) {
		refreshInterval = seconds;
	}

	public boolean isKeepRunning() {
		return keepRunning;
	}

	public void setKeepRunning(boolean run) {
		keepRunning = run;
	}

	public LocalDateTime getHeartbeat() {
		return lastRunTime;
	}

	public int getSize() {
		return list.size();
	}

	public synchronized int getNextConnectionId() {
		return ++lastConnectionId;
	}
}
