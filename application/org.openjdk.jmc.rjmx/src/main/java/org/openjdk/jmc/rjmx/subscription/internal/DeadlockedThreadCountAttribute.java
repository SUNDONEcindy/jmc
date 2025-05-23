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
package org.openjdk.jmc.rjmx.subscription.internal;

import java.lang.management.ManagementFactory;
import java.util.logging.Level;

import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.openjdk.jmc.rjmx.RJMXPlugin;

/**
 * Wrapping attribute://java.lang:type=Threading/findDeadlockedThreads invocations as a numeric
 * attribute.
 */
public class DeadlockedThreadCountAttribute extends AbstractSyntheticAttribute {

	private static final int METHOD_INVOCATION_ERROR = -1;
	private static final int METHOD_NOT_PRESENT = -2;

	// Keep track of if the method is unavailable through reflection so that we can avoid trying to call it
	private boolean hasLoggedReflectionError = false;

	@Override
	public Object getValue(MBeanServerConnection connection) {
		if (hasLoggedReflectionError) {
			return METHOD_NOT_PRESENT;
		}
		return getDeadlockedThreadCount(connection);
	}

	private int getDeadlockedThreadCount(MBeanServerConnection connection) {
		try {
			ObjectName threadMBean = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
			Object result = connection.invoke(threadMBean, "findDeadlockedThreads", null, null); //$NON-NLS-1$
			if (result != null && result instanceof long[]) {
				return ((long[]) result).length;
			}
			return 0;
		} catch (ReflectionException e) {
			if (!hasLoggedReflectionError) {
				hasLoggedReflectionError = true;
				RJMXPlugin.getDefault().getLogger().log(Level.SEVERE,
						"Unable to find findDeadlockedThreads(). Are you running a JVM version < 1.6?", e); //$NON-NLS-1$
			}
			return METHOD_NOT_PRESENT;
		} catch (Exception e) {
			RJMXPlugin.getDefault().getLogger().log(Level.SEVERE,
					"Unable to invoke findDeadlockedThreads(). Are you running a JVM version < 1.6?", e); //$NON-NLS-1$
			return METHOD_INVOCATION_ERROR;
		}
	}

	@Override
	public void setValue(MBeanServerConnection connection, Object value) {
		// value can not be set
	}

	@Override
	public boolean hasResolvedDependencies(MBeanServerConnection connection) {
		try {
			ObjectName threadMBean = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
			for (MBeanOperationInfo operation : connection.getMBeanInfo(threadMBean).getOperations()) {
				if (operation.getName().equals("findDeadlockedThreads") && operation.getSignature().length == 0) { //$NON-NLS-1$
					return true;
				}
			}
		} catch (Exception e) {
		}
		return false;
	}
}
