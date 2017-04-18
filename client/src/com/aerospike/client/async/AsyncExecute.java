/*
 * Copyright 2012-2017 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.client.async;

import java.util.Map;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Value;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.listener.ExecuteListener;
import com.aerospike.client.policy.WritePolicy;

public final class AsyncExecute extends AsyncRead {
	private final WritePolicy writePolicy;
	private final ExecuteListener executeListener;
	private final String packageName;
	private final String functionName;
	private final Value[] args;
		
	public AsyncExecute(
		AsyncCluster cluster,
		WritePolicy writePolicy,
		ExecuteListener listener,
		Key key,
		String packageName,
		String functionName,
		Value[] args
	) {
		super(cluster, writePolicy, null, key, null);
		this.writePolicy = writePolicy;
		this.executeListener = listener;
		this.packageName = packageName;
		this.functionName = functionName;
		this.args = args;
	}
	
	public AsyncExecute(AsyncExecute other) {
		super(other);
		this.writePolicy = other.writePolicy;
		this.executeListener = other.executeListener;
		this.packageName = other.packageName;
		this.functionName = other.functionName;
		this.args = other.args;
	}

	@Override
	protected AsyncCommand cloneCommand() {
		return new AsyncExecute(this);
	}

	@Override
	protected void writeBuffer() throws AerospikeException {
		setUdf(writePolicy, key, packageName, functionName, args);
	}

	@Override
	protected Node getNode() {	
		return cluster.getMasterNode(partition);
	}

	@Override
	protected void handleNotFound(int resultCode) {
    	throw new AerospikeException(resultCode);
	}

	@Override
	protected void onSuccess() {
		if (executeListener != null) {
			Object obj = parseEndResult();
			executeListener.onSuccess(key, obj);
		}
	}
	
	@Override
	protected void onFailure(AerospikeException e) {
		if (executeListener != null) {
			executeListener.onFailure(e);
		}
	}

	private Object parseEndResult() {
		if (record == null || record.bins == null) {
			return null;
		}
		
		Map<String,Object> map = record.bins;

		Object obj = map.get("SUCCESS");
		
		if (obj != null) {
			return obj;
		}
		
		// User defined functions don't have to return a value.
		if (map.containsKey("SUCCESS")) {
			return null;
		}
		
		obj = map.get("FAILURE");
		
		if (obj != null) {
			throw new AerospikeException(obj.toString());
		}
		throw new AerospikeException("Invalid UDF return value");		
	}
}
