/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.udf.generic;

import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.parse.WindowingSpec.BoundarySpec;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator.AggregationBuffer;
import org.apache.hadoop.hive.ql.util.JavaDataModel;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;

@SuppressWarnings({ "deprecation", "unchecked" })
public abstract class GenericUDAFStreamingEnhancer<T1, T2> extends
    GenericUDAFEvaluator implements ISupportStreamingModeForWindowing {

  private final GenericUDAFEvaluator wrappedEval;
  private final int numPreceding;
  private final int numFollowing;

  public GenericUDAFStreamingEnhancer(GenericUDAFEvaluator wrappedEval,
      int numPreceding, int numFollowing) {
    this.wrappedEval = wrappedEval;
    this.numPreceding = numPreceding;
    this.numFollowing = numFollowing;
    this.mode = wrappedEval.mode;
  }

  class StreamingState extends AbstractAggregationBuffer {
    final AggregationBuffer wrappedBuf;
    final int numPreceding;
    final int numFollowing;
    final List<T1> results;
    final List<T2> intermediateVals;
    int numRows;

    StreamingState(int numPreceding, int numFollowing, AggregationBuffer buf) {
      this.wrappedBuf = buf;
      this.numPreceding = numPreceding;
      this.numFollowing = numFollowing;
      results = new ArrayList<T1>();
      intermediateVals = new ArrayList<T2>();
      numRows = 0;
    }

    @Override
    public int estimate() {
      if (!(wrappedBuf instanceof AbstractAggregationBuffer)) {
        return -1;
      }
      int underlying = ((AbstractAggregationBuffer) wrappedBuf).estimate();
      if (underlying == -1) {
        return -1;
      }
      if (numPreceding == BoundarySpec.UNBOUNDED_AMOUNT) {
        return -1;
      }
      /*
       * sz Estimate = sz needed by underlying AggBuffer +
       *                 sz for results +
       *                 sz for intermediates +
       *                 3 * JavaDataModel.PRIMITIVES1
       * sz of results = sz of underlying * wdwSz
       * sz of intermediates = sz of underlying * wdwSz
       */

      int wdwSz = numPreceding + numFollowing + 1;
      return underlying + 
          (underlying * wdwSz) +
          (underlying * wdwSz) +
          (3 * JavaDataModel.PRIMITIVES1);
    }
  }

  @Override
  public ObjectInspector init(Mode m, ObjectInspector[] parameters)
      throws HiveException {
    throw new HiveException(getClass().getSimpleName() + ": init not supported");
  }

  @Override
  public AggregationBuffer getNewAggregationBuffer() throws HiveException {
    AggregationBuffer underlying = wrappedEval.getNewAggregationBuffer();
    return new StreamingState(numPreceding, numFollowing, underlying);
  }

  @Override
  public void reset(AggregationBuffer agg) throws HiveException {
    StreamingState ss = (StreamingState) agg;
    wrappedEval.reset(ss.wrappedBuf);
    ss.results.clear();
    ss.intermediateVals.clear();
    ss.numRows = 0;
  }

  @Override
  public void iterate(AggregationBuffer agg, Object[] parameters)
      throws HiveException {
    StreamingState ss = (StreamingState) agg;

    wrappedEval.iterate(ss.wrappedBuf, parameters);

    if (ss.numRows >= ss.numFollowing) {
      ss.results.add(getNextResult(ss));
    }
    if (ss.numPreceding != BoundarySpec.UNBOUNDED_AMOUNT) {
      ss.intermediateVals.add(getCurrentIntermediateResult(ss));
    }

    ss.numRows++;
  }

  @Override
  public Object terminate(AggregationBuffer agg) throws HiveException {
    StreamingState ss = (StreamingState) agg;
    Object o = wrappedEval.terminate(ss.wrappedBuf);

    for (int i = 0; i < ss.numFollowing; i++) {
      ss.results.add(getNextResult(ss));
    }
    return o;
  }

  @Override
  public Object terminatePartial(AggregationBuffer agg) throws HiveException {
    throw new HiveException(getClass().getSimpleName()
        + ": terminatePartial not supported");
  }

  @Override
  public void merge(AggregationBuffer agg, Object partial) throws HiveException {
    throw new HiveException(getClass().getSimpleName()
        + ": merge not supported");
  }

  @Override
  public Object getNextResult(AggregationBuffer agg) throws HiveException {
    StreamingState ss = (StreamingState) agg;
    if (!ss.results.isEmpty()) {
      T1 res = ss.results.remove(0);
      if (res == null) {
        return ISupportStreamingModeForWindowing.NULL_RESULT;
      }
      return res;
    }
    return null;
  }

  protected abstract T1 getNextResult(StreamingState ss) throws HiveException;

  protected abstract T2 getCurrentIntermediateResult(StreamingState ss)
      throws HiveException;
}