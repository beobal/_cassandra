/*
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

package org.apache.cassandra.simulator.systems;

import java.util.UUID;

import org.apache.cassandra.simulator.RandomSource;
import org.apache.cassandra.utils.concurrent.Condition;
import org.apache.cassandra.utils.concurrent.CountDownLatch;
import org.apache.cassandra.utils.concurrent.WaitQueue;

import static org.apache.cassandra.simulator.systems.InterceptedWait.Kind.NEMESIS;

@PerClassLoader
public class InterceptingGlobalMethods extends InterceptingMonitors implements InterceptorOfGlobalMethods
{
    private int uniqueUuidCounter = 0;
    public InterceptingGlobalMethods(InterceptorOfWaits interceptorOfWaits, RandomSource random)
    {
        super(interceptorOfWaits, random);
    }

    @Override
    public WaitQueue newWaitQueue()
    {
        return new InterceptingWaitQueue(interceptorOfWaits);
    }

    @Override
    public CountDownLatch newCountDownLatch(int count)
    {
        return new InterceptingAwaitable.InterceptingCountDownLatch(interceptorOfWaits, count);
    }

    @Override
    public Condition newOneTimeCondition()
    {
        return new InterceptingAwaitable.InterceptingCondition(interceptorOfWaits);
    }

    @Override
    public void nemesis(float chance)
    {
        InterceptibleThread thread = interceptorOfWaits.ifIntercepted();
        if (thread == null || thread.isEvaluationDeterministic() || !random.decide(chance))
            return;

        InterceptedWait.InterceptedConditionWait signal = new InterceptedWait.InterceptedConditionWait(NEMESIS, 0L, thread, interceptorOfWaits.captureWaitSite(thread), null);
        thread.interceptWait(signal);

        // save interrupt state to restore afterwards - new ones only arrive if terminating simulation
        boolean wasInterrupted = Thread.interrupted();
        signal.awaitThrowUncheckedOnInterrupt();
        if (wasInterrupted) thread.interrupt();
    }

    @Override
    public long randomSeed()
    {
        InterceptibleThread thread = interceptorOfWaits.ifIntercepted();
        if (thread == null || thread.isEvaluationDeterministic())
            return Thread.currentThread().getName().hashCode();

        return random.uniform(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    @Override
    public synchronized UUID randomUUID()
    {
        long msb = random.uniform(0, 1L << 60);
        msb = ((msb << 4) & 0xffffffffffff0000L) | 0x4000 | (msb & 0xfff);
        return new UUID(msb, (1L << 63) | uniqueUuidCounter++);
    }
}
