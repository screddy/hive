/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hive.spark.client;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hive.spark.counter.SparkCounters;

import org.apache.spark.api.java.JavaFutureAction;
import org.apache.spark.api.java.JavaSparkContext;

class JobContextImpl implements JobContext {

  private final JavaSparkContext sc;
  private final ThreadLocal<MonitorCallback> monitorCb;
  private final Map<String, List<JavaFutureAction<?>>> monitoredJobs;

  public JobContextImpl(JavaSparkContext sc) {
    this.sc = sc;
    this.monitorCb = new ThreadLocal<MonitorCallback>();
    monitoredJobs = new ConcurrentHashMap<String, List<JavaFutureAction<?>>>();
  }


  @Override
  public JavaSparkContext sc() {
    return sc;
  }

  @Override
  public <T> JavaFutureAction<T> monitor(JavaFutureAction<T> job,
      SparkCounters sparkCounters, Set<Integer> cachedRDDIds) {
    monitorCb.get().call(job, sparkCounters, cachedRDDIds);
    return job;
  }

  @Override
  public Map<String, List<JavaFutureAction<?>>> getMonitoredJobs() {
    return monitoredJobs;
  }

  void setMonitorCb(MonitorCallback cb) {
    monitorCb.set(cb);
  }

  void stop() {
    monitoredJobs.clear();
    sc.stop();
  }

}
