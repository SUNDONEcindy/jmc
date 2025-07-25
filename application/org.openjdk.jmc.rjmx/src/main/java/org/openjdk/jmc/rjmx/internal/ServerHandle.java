/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The contents of this file are subject to the terms of either the Universal Permissive License
 * v 1.0 as shown at https://oss.oracle.com/licenses/upl
 *
 * or the following license:
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.openjdk.jmc.rjmx.internal;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import javax.management.remote.JMXConnector;

import org.openjdk.jmc.common.io.IOToolkit;
import org.openjdk.jmc.rjmx.IServerHandle;
import org.openjdk.jmc.rjmx.RJMXPlugin;
import org.openjdk.jmc.rjmx.common.ConnectionException;
import org.openjdk.jmc.rjmx.common.ConnectionToolkit;
import org.openjdk.jmc.rjmx.common.IConnectionDescriptor;
import org.openjdk.jmc.rjmx.common.IConnectionHandle;
import org.openjdk.jmc.rjmx.common.IConnectionListener;
import org.openjdk.jmc.rjmx.common.IServerDescriptor;
import org.openjdk.jmc.rjmx.common.internal.DefaultConnectionHandle;
import org.openjdk.jmc.rjmx.common.internal.RJMXConnection;
import org.openjdk.jmc.rjmx.common.internal.ServerDescriptor;

/**
 * Implementation of {@link IServerHandle} that manages JMX server connections.
 * <p>
 * This class implements automatic resource cleanup using the {@link Cleaner} API. While the cleaner
 * provides a safety net for resource cleanup, it is strongly recommended to explicitly call
 * {@link #dispose()} for predictable resource management.
 */
public final class ServerHandle implements IServerHandle {

	private final List<DefaultConnectionHandle> connectionHandles = new ArrayList<>();
	private final RJMXConnection connection;
	private final Runnable observer;
	private Boolean disposedGracefully; // null if not yet disposed
	private final Cleaner.Cleanable cleanable;
	private final Runnable connectionListener = new Runnable() {

		@Override
		public void run() {
			disconnect();
		}
	};
	private final IConnectionListener connectionHandleListener = new IConnectionListener() {

		@Override
		public void onConnectionChange(IConnectionHandle handle) {
			if (!handle.isConnected() && removeConnectionHandle(handle)) {
				nofifyObserver();
			}
		}
	};

	public ServerHandle(IConnectionDescriptor descriptor) {
		this(new ServerDescriptor(), descriptor, null);
	}

	public ServerHandle(IServerDescriptor server, IConnectionDescriptor descriptor, Runnable observer) {
		this.observer = observer;
		connection = new RJMXConnection(descriptor, server, connectionListener,
				SyntheticRepositoryInitializer.initializeAttributeEntries(),
				SyntheticRepositoryInitializer.initializeNotificationEntries());
		this.cleanable = ConnectionToolkit.CLEANER.register(this, new CleanupAction(connection, connectionHandles));
	}

	public IConnectionDescriptor getConnectionDescriptor() {
		return connection.getConnectionDescriptor();
	}

	@Override
	public IServerDescriptor getServerDescriptor() {
		return connection.getServerDescriptor();
	}

	public synchronized IConnectionHandle[] getConnectionHandles() {
		IConnectionHandle[] handles = new IConnectionHandle[connectionHandles.size()];
		Iterator<DefaultConnectionHandle> it = connectionHandles.iterator();
		for (int i = 0; i < handles.length; i++) {
			handles[i] = it.next();
		}
		return handles;
	}

	@Override
	public IConnectionHandle connect(String usage) throws ConnectionException {
		IConnectionListener[] listeners = new IConnectionListener[] {connectionHandleListener};
		return doConnect(usage, listeners);
	}

	@Override
	public IConnectionHandle connect(String usage, IConnectionListener listener) throws ConnectionException {
		IConnectionListener[] listeners = new IConnectionListener[] {listener, connectionHandleListener};
		return doConnect(usage, listeners);
	}

	private IConnectionHandle doConnect(String usage, IConnectionListener[] listeners) throws ConnectionException {
		boolean performedConnect;
		DefaultConnectionHandle newConnectionHandle;
		synchronized (this) {
			if (isDisposed()) {
				throw new ConnectionException("Server handle is disposed"); //$NON-NLS-1$
			}
			JMXConnector overriddenConnection = this.checkForProtocolSpecificConnectorExtension();
			if (overriddenConnection != null) {
				connection.specifyConnector(overriddenConnection);
			}
			performedConnect = connection.connect();
			newConnectionHandle = new DefaultConnectionHandle(connection, usage, listeners,
					ServiceFactoryInitializer.initializeFromExtensions());
			connectionHandles.add(newConnectionHandle);
		}
		if (performedConnect) {
			nofifyObserver();
		}
		return newConnectionHandle;
	}

	private JMXConnector checkForProtocolSpecificConnectorExtension() {
		final IConnectionDescriptor descriptor = this.connection.getConnectionDescriptor();
		try {
			return new ProtocolInitializer().newJMXConnector(descriptor.createJMXServiceURL(),
					descriptor.getEnvironment());
		} catch (IOException e) {
			RJMXPlugin.getDefault().getLogger().log(Level.INFO, "Error attempting JMX protocol extensions", e);
			return null;
		}
	}

	public void dispose(boolean gracefully) {
		try {
			synchronized (this) {
				if (isDisposed()) {
					return;
				}
				disposedGracefully = gracefully;
			}
			disconnect();
		} finally {
			cleanable.clean();
		}
	}

	@Override
	public void dispose() {
		dispose(true);
	}

	private synchronized boolean isDisposed() {
		return disposedGracefully != null;
	}

	private synchronized boolean removeConnectionHandle(IConnectionHandle handle) {
		connectionHandles.remove(handle);
		if (connectionHandles.size() == 0) {
			connection.close();
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		disconnect();
	}

	private void disconnect() {
		for (IConnectionHandle handle : getConnectionHandles()) {
			IOToolkit.closeSilently(handle);
		}
	}

	@Override
	public String toString() {
		return connection.toString();
	}

	private void nofifyObserver() {
		if (observer != null) {
			observer.run();
		}
	}

	@Override
	public synchronized State getState() {
		if (isDisposed()) {
			return disposedGracefully ? State.DISPOSED : State.FAILED;
		} else {
			return connection.isConnected() ? State.CONNECTED : State.DISCONNECTED;
		}
	}

	private static class CleanupAction implements Runnable {
		private final List<DefaultConnectionHandle> connectionHandles;

		CleanupAction(RJMXConnection connection, List<DefaultConnectionHandle> connectionHandles) {
			this.connectionHandles = new ArrayList<>(connectionHandles);
		}

		@Override
		public void run() {
			try {
				disconnectQuietly();
			} catch (Exception e) {
				// Ignore all exceptions during cleanup
			}
		}

		private void disconnectQuietly() {
			for (DefaultConnectionHandle handle : connectionHandles) {
				try {
					IOToolkit.closeSilently(handle);
				} catch (Exception e) {
					// Ignore exceptions during cleanup
				}
			}
		}
	}
}
