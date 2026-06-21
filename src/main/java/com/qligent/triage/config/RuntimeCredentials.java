package com.qligent.triage.config;

import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Component;

@Component
public class RuntimeCredentials {

	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private String zephyrApiToken;
	private String jiraApiUrl;
	private String jiraEmail;
	private String jiraApiToken;

	public RuntimeCredentials(ZephyrProperties zephyrProperties, JiraProperties jiraProperties) {
		this.zephyrApiToken = blankToNull(zephyrProperties.apiToken());
		this.jiraApiUrl = blankToNull(jiraProperties.apiUrl());
		this.jiraEmail = blankToNull(jiraProperties.email());
		this.jiraApiToken = blankToNull(jiraProperties.apiToken());
	}

	public boolean isZephyrConfigured() {
		lock.readLock().lock();
		try {
			return zephyrApiToken != null && !zephyrApiToken.isBlank();
		} finally {
			lock.readLock().unlock();
		}
	}

	public boolean isJiraConfigured() {
		lock.readLock().lock();
		try {
			return jiraApiUrl != null && !jiraApiUrl.isBlank()
					&& jiraEmail != null && !jiraEmail.isBlank()
					&& jiraApiToken != null && !jiraApiToken.isBlank();
		} finally {
			lock.readLock().unlock();
		}
	}

	public Optional<String> zephyrApiToken() {
		lock.readLock().lock();
		try {
			return Optional.ofNullable(zephyrApiToken);
		} finally {
			lock.readLock().unlock();
		}
	}

	public Optional<String> jiraApiUrl() {
		lock.readLock().lock();
		try {
			return Optional.ofNullable(jiraApiUrl);
		} finally {
			lock.readLock().unlock();
		}
	}

	public Optional<String> jiraEmail() {
		lock.readLock().lock();
		try {
			return Optional.ofNullable(jiraEmail);
		} finally {
			lock.readLock().unlock();
		}
	}

	public Optional<String> jiraApiToken() {
		lock.readLock().lock();
		try {
			return Optional.ofNullable(jiraApiToken);
		} finally {
			lock.readLock().unlock();
		}
	}

	public void update(String zephyrToken, String jiraUrl, String email, String jiraToken) {
		lock.writeLock().lock();
		try {
			this.zephyrApiToken = blankToNull(zephyrToken);
			this.jiraApiUrl = blankToNull(jiraUrl);
			this.jiraEmail = blankToNull(email);
			this.jiraApiToken = blankToNull(jiraToken);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
