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
package org.openjdk.jmc.rjmx.triggers;

import org.openjdk.jmc.rjmx.common.IConnectionHandle;
import org.openjdk.jmc.rjmx.common.subscription.MRI;
import org.openjdk.jmc.rjmx.common.subscription.MRIValueEvent;

/**
 * Interface for triggers.
 */
public interface ITrigger {

	/**
	 * This method determines if any action should be taken on the incoming event.
	 *
	 * @param connectionHandle
	 *            the connection for which to trigger.
	 * @param rule
	 *            the rule to trigger.
	 * @param attributeEvent
	 *            the attribute event to trigger on.
	 */
	void triggerOn(IConnectionHandle connectionHandle, TriggerRule rule, MRIValueEvent attributeEvent);

	/**
	 * Returns the attribute descriptor for which the trigger was registered.
	 *
	 * @return the attribute descriptor for which the trigger was registered.
	 */
	MRI getAttributeDescriptor();

	/**
	 * Returns the value evaluator for the trigger.
	 *
	 * @return the value evaluator for the trigger.
	 */
	IValueEvaluator getValueEvaluator();

	/**
	 * Returns the sustain time, i.e. the time a signal has to remain high/low before we trigger.
	 *
	 * @return the sustain time, i.e. the time a signal has to remain high/low before we trigger.
	 */
	int getSustainTime();
}
